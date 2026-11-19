package com.crmplatform.reporting;

import static com.crmplatform.support.Api.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmplatform.support.Api;
import com.crmplatform.support.Api.Session;
import com.crmplatform.support.IntegrationTest;
import com.crmplatform.support.Invites;
import com.crmplatform.support.RecordingEmailSender;
import com.jayway.jsonpath.JsonPath;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** R12: Arabic visit report PDF and the dashboard. */
@IntegrationTest
class ReportsIT {

    private static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");

    @Autowired
    MockMvc mvc;
    @Autowired
    RecordingEmailSender emails;

    @Test
    void visitReportIsARealArabicPdfWithPhotosAndOnlyForThoseWhoMaySeeTheVisit() throws Exception {
        Session owner = Api.register(mvc, Api.uniqueEmail(), "شركة النور للصيانة");
        Session tech = Invites.invite(mvc, emails, owner, "TECHNICIAN");
        Session otherTech = Invites.invite(mvc, emails, owner, "TECHNICIAN");
        String customer = id(mvc.perform(json(post("/api/v1/contacts"), """
                {"type":"CLIENT","kind":"COMPANY","company":"مستشفى النيل","phone":"+20223456789"}
                """).with(owner.auth())));
        String site = id(mvc.perform(json(post("/api/v1/locations"), """
                {"contactId":"%s","type":"COMMERCIAL","name":"المبنى الرئيسي","address":{"line1":"شارع التحرير","city":"القاهرة","country":"EG"}}
                """.formatted(customer)).with(owner.auth())));
        String job = id(mvc.perform(json(post("/api/v1/jobs"), """
                {"contactId":"%s","locationId":"%s","title":"صيانة كاميرات المراقبة","budget":"2000",
                 "internalNotes":"ملاحظة داخلية سرية","assigneeUserIds":["%s"]}
                """.formatted(customer, site, tech.userId())).with(owner.auth())).andExpect(status().isCreated()));
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        String visit = id(mvc.perform(json(post("/api/v1/jobs/" + job + "/visits"), """
                {"scheduledStart":"%s","scheduledEnd":"%s","assigneeUserIds":["%s"]}
                """.formatted(start, start.plus(2, ChronoUnit.HOURS), tech.userId())).with(owner.auth())));

        String before = upload(tech, "BEFORE", Color.RED);
        String after = upload(tech, "AFTER", Color.BLUE);
        mvc.perform(json(post("/api/v1/visits/" + visit + "/files"), "{\"fileIds\":[\"%s\",\"%s\"]}"
                .formatted(before, after)).with(tech.auth())).andExpect(status().isOk());
        String signature = upload(tech, null, Color.BLACK);
        mvc.perform(json(put("/api/v1/visits/" + visit + "/report"), """
                {"reportedProblem":"الكاميرا رقم 3 لا تعمل","findings":"تلف في كابل الشبكة",
                 "diagnosis":"انقطاع الكابل","workPerformed":"استبدال الكابل وضبط الزاوية",
                 "recommendations":"فحص دوري كل 6 أشهر","customerName":"أ. سامي","customerSignatureFileId":"%s"}
                """.formatted(signature)).with(tech.auth())).andExpect(status().isOk());
        mvc.perform(json(post("/api/v1/jobs/" + job + "/materials"), """
                {"visitId":"%s","name":"كابل شبكة Cat6","quantity":"15","unit":"متر","unitCost":"987.65"}
                """.formatted(visit)).with(tech.auth())).andExpect(status().isCreated());

        // the assigned technician downloads it; Arabic by default (workspace + request language)
        byte[] pdf = mvc.perform(get("/api/v1/reports/visits/" + visit + "/pdf").with(tech.auth()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment; filename=\"visit-report-")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(fontNames(doc)).anyMatch(n -> n.contains("IBMPlexSansArabic")); // embedded Arabic font
            assertThat(imageCount(doc)).isGreaterThanOrEqualTo(3); // 2 photos + signature
            // presentation forms (shaped glyphs) → base letters; PDFBox returns RTL runs in logical order
            String text = Normalizer.normalize(new PDFTextStripper().getText(doc), Normalizer.Form.NFKC);
            assertThat(text).contains("تقرير زيارة فنية", "مستشفى النيل", "صيانة كاميرات المراقبة",
                    "استبدال الكابل وضبط الزاوية", "أ. سامي", "قبل التنفيذ", "بعد التنفيذ", "كابل شبكة");
            // internal data never printed
            assertThat(text).doesNotContain("ملاحظة داخلية سرية").doesNotContain("987");
            // keep pictures of every page for a human check (target/, not committed)
            Path out = Path.of("target", "reports-it");
            Files.createDirectories(out);
            for (int p = 0; p < doc.getNumberOfPages(); p++) {
                ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(p, 110), "png",
                        out.resolve("visit-report-ar-page" + (p + 1) + ".png").toFile());
            }
        }

        // English on request
        byte[] en = mvc.perform(get("/api/v1/reports/visits/" + visit + "/pdf?lang=en").with(owner.auth()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (PDDocument doc = Loader.loadPDF(en)) {
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("Technical visit report", "Work performed", "Customer confirmation");
            ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(0, 110), "png",
                    Path.of("target", "reports-it", "visit-report-en-page1.png").toFile());
        }

        // access: another technician (not assigned), another workspace, no session
        mvc.perform(get("/api/v1/reports/visits/" + visit + "/pdf").with(otherTech.auth()))
                .andExpect(status().isNotFound());
        Session stranger = Api.register(mvc);
        mvc.perform(get("/api/v1/reports/visits/" + visit + "/pdf").with(stranger.auth()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/reports/visits/" + visit + "/pdf")).andExpect(status().isUnauthorized());
    }

    @Test
    void dashboardCountsOnlyTheCallersWorkspaceAndHidesMoneyWithoutFinanceRead() throws Exception {
        Session owner = Api.register(mvc);
        String customer = id(mvc.perform(json(post("/api/v1/contacts"), """
                {"type":"CLIENT","kind":"COMPANY","company":"Co","phone":"+20223456781"}
                """).with(owner.auth())));
        String site = id(mvc.perform(json(post("/api/v1/locations"), """
                {"contactId":"%s","type":"COMMERCIAL","name":"HQ","address":{"line1":"x","city":"Cairo","country":"EG"}}
                """.formatted(customer)).with(owner.auth())));
        String a = id(mvc.perform(json(post("/api/v1/jobs"), """
                {"contactId":"%s","locationId":"%s","title":"A","budget":"2000"}
                """.formatted(customer, site)).with(owner.auth())));
        id(mvc.perform(json(post("/api/v1/jobs"), """
                {"contactId":"%s","locationId":"%s","title":"B","budget":"1000"}
                """.formatted(customer, site)).with(owner.auth())));
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        mvc.perform(json(post("/api/v1/jobs/" + a + "/visits"), """
                {"scheduledStart":"%s","scheduledEnd":"%s"}
                """.formatted(start, start.plus(1, ChronoUnit.HOURS))).with(owner.auth()))
                .andExpect(status().isCreated());
        String today = LocalDate.now(CAIRO).toString();
        mvc.perform(json(post("/api/v1/transactions"), """
                {"jobId":"%s","type":"RECEIVED","amount":"500","title":"دفعة","payee":"العميل","method":"CASH",
                 "commitmentDate":"%s","dueDate":"%s"}
                """.formatted(a, today, today)).with(owner.auth())).andExpect(status().isCreated());

        // noise in another workspace must not be counted
        Session other = Api.register(mvc);
        String oc = id(mvc.perform(json(post("/api/v1/contacts"), """
                {"type":"CLIENT","kind":"COMPANY","company":"Other","phone":"+20223456782"}
                """).with(other.auth())));
        String os = id(mvc.perform(json(post("/api/v1/locations"), """
                {"contactId":"%s","type":"COMMERCIAL","name":"HQ","address":{"line1":"x","city":"Cairo","country":"EG"}}
                """.formatted(oc)).with(other.auth())));
        id(mvc.perform(json(post("/api/v1/jobs"), """
                {"contactId":"%s","locationId":"%s","title":"X","budget":"99999"}
                """.formatted(oc, os)).with(other.auth())));

        boolean visitIsToday = start.atZone(CAIRO).toLocalDate().equals(LocalDate.now(CAIRO));
        mvc.perform(get("/api/v1/reports/dashboard").with(owner.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("MONTH"))
                .andExpect(jsonPath("$.timeZone").value("Africa/Cairo"))
                .andExpect(jsonPath("$.jobs.open").value(2))
                .andExpect(jsonPath("$.jobs.created").value(2))
                .andExpect(jsonPath("$.jobs.openByStatus.NEW").value(1))
                .andExpect(jsonPath("$.jobs.openByStatus.SCHEDULED").value(1)) // scheduling moved job A
                .andExpect(jsonPath("$.jobs.openByStatus.ON_HOLD").value(0))
                .andExpect(jsonPath("$.visits.today").value(visitIsToday ? 1 : 0))
                .andExpect(jsonPath("$.visits.overdue").value(0))
                .andExpect(jsonPath("$.finance.received").value("500.00"))
                .andExpect(jsonPath("$.finance.outstanding").value("2500.00")); // 3000 contract − 500
        mvc.perform(get("/api/v1/reports/dashboard?period=ALL").with(owner.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").doesNotExist()) // ALL has no start
                .andExpect(jsonPath("$.jobs.open").value(2));
        mvc.perform(get("/api/v1/reports/dashboard?period=DECADE").with(owner.auth()))
                .andExpect(status().isBadRequest());

        // permissions: technician and member have no reports:read; finance sees money
        Session tech = Invites.invite(mvc, emails, owner, "TECHNICIAN");
        mvc.perform(get("/api/v1/reports/dashboard").with(tech.auth())).andExpect(status().isForbidden());
        Session member = Invites.invite(mvc, emails, owner, "MEMBER");
        mvc.perform(get("/api/v1/reports/dashboard").with(member.auth())).andExpect(status().isForbidden());
        Session finance = Invites.invite(mvc, emails, owner, "FINANCE");
        mvc.perform(get("/api/v1/reports/dashboard?period=WEEK").with(finance.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finance.received").value("500.00"));
    }

    private String upload(Session s, String purpose, Color color) throws Exception {
        BufferedImage image = new BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(100, 100, 1000, 600);
        g.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        var request = multipart("/api/v1/files")
                .file(new MockMultipartFile("files", "photo.png", "image/png", png.toByteArray()))
                .param("kind", "IMAGE");
        if (purpose != null) {
            request.param("purpose", purpose);
        }
        String body = mvc.perform(request.with(s.auth()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.files[0].id");
    }

    private static Set<String> fontNames(PDDocument doc) throws Exception {
        Set<String> names = new HashSet<>();
        for (PDPage page : doc.getPages()) {
            for (COSName n : page.getResources().getFontNames()) {
                PDFont f = page.getResources().getFont(n);
                names.add(f.getName());
            }
        }
        return names;
    }

    private static int imageCount(PDDocument doc) throws Exception {
        int count = 0;
        for (PDPage page : doc.getPages()) {
            for (COSName n : page.getResources().getXObjectNames()) {
                if (page.getResources().getXObject(n) instanceof PDImageXObject) {
                    count++;
                }
            }
        }
        return count;
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
