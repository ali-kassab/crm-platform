package com.crmplatform.operations;

import static com.crmplatform.support.Api.json;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmplatform.support.Api;
import com.crmplatform.support.Api.Session;
import com.crmplatform.support.IntegrationTest;
import com.crmplatform.support.Invites;
import com.crmplatform.support.RecordingEmailSender;
import com.jayway.jsonpath.JsonPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** R4: visit reports, photos by purpose with thumbnails, materials in profit, approvals, technician file access. */
@IntegrationTest
class FieldEvidenceIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    RecordingEmailSender emails;

    @Test
    void technicianDocumentsAVisitAndMaterialsCountInProfit() throws Exception {
        Session owner = Api.register(mvc);
        Session tech = Invites.invite(mvc, emails, owner, "TECHNICIAN");
        String customer = id(mvc.perform(json(post("/api/v1/contacts"), """
                {"type":"CLIENT","kind":"COMPANY","company":"فندق النيل","phone":"+20223456789"}
                """).with(owner.auth())));
        String site = id(mvc.perform(json(post("/api/v1/locations"), """
                {"contactId":"%s","type":"COMMERCIAL","name":"المبنى أ","address":{"city":"القاهرة","country":"EG"}}
                """.formatted(customer)).with(owner.auth())));
        String job = id(mvc.perform(json(post("/api/v1/jobs"), """
                {"contactId":"%s","locationId":"%s","title":"صيانة تكييف","budget":"2000","assigneeUserIds":["%s"]}
                """.formatted(customer, site, tech.userId())).with(owner.auth())).andExpect(status().isCreated()));
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        String visit = id(mvc.perform(json(post("/api/v1/jobs/" + job + "/visits"), """
                {"scheduledStart":"%s","scheduledEnd":"%s"}
                """.formatted(start, start.plus(2, ChronoUnit.HOURS))).with(owner.auth())));

        // photos: purpose kept, images get a thumbnail
        String before = upload(tech, "BEFORE");
        mvc.perform(get("/api/v1/files/" + before).with(tech.auth()))
                .andExpect(jsonPath("$.purpose").value("BEFORE"))
                .andExpect(jsonPath("$.hasThumbnail").value(true));
        mvc.perform(get("/api/v1/files/" + before + "/thumbnail-url").with(tech.auth())).andExpect(status().isOk());
        String after = upload(tech, "AFTER");
        mvc.perform(json(post("/api/v1/visits/" + visit + "/files"), "{\"fileIds\":[\"%s\",\"%s\"]}".formatted(before, after))
                .with(tech.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // technical report in Arabic with the customer's confirmation
        mvc.perform(json(put("/api/v1/visits/" + visit + "/report"), """
                {"reportedProblem":"التكييف لا يبرد","findings":"نقص في غاز الفريون","diagnosis":"تسريب في الوصلة",
                 "workPerformed":"لحام الوصلة وشحن الغاز","recommendations":"صيانة كل 3 أشهر","customerName":"أ. سامي"}
                """).with(tech.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workPerformed").value("لحام الوصلة وشحن الغاز"))
                .andExpect(jsonPath("$.customerName").value("أ. سامي"))
                .andExpect(jsonPath("$.customerConfirmedAt").isNotEmpty());
        mvc.perform(get("/api/v1/visits/" + visit + "/report").with(owner.auth()))
                .andExpect(jsonPath("$.diagnosis").value("تسريب في الوصلة"));

        // materials (D12): cost lines that reduce profit, not transactions
        mvc.perform(json(post("/api/v1/jobs/" + job + "/materials"), """
                {"visitId":"%s","name":"غاز فريون R410","quantity":"2.5","unit":"كجم","unitCost":"120"}
                """.formatted(visit)).with(tech.auth()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lineCost").value("300.00"));
        mvc.perform(json(post("/api/v1/jobs/" + job + "/materials"), "{\"name\":\"x\",\"quantity\":\"0\"}")
                .with(tech.auth())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/jobs/" + job + "/materials").with(owner.auth()))
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andExpect(jsonPath("$.totalCost").value("300.00"));
        mvc.perform(get("/api/v1/jobs/" + job + "/accounting").with(owner.auth()))
                .andExpect(jsonPath("$.materialsCost").value("300.00"))
                .andExpect(jsonPath("$.profitLoss").value("1700.00"))
                .andExpect(jsonPath("$.transactionCount").value(0));

        // approvals (D14): recorded on site, decided once
        String approval = id(mvc.perform(json(post("/api/v1/jobs/" + job + "/approvals"), """
                {"kind":"COMPLETION","decision":{"status":"APPROVED","approverName":"أ. سامي"}}
                """).with(tech.auth()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED")));
        mvc.perform(json(post("/api/v1/jobs/" + job + "/approvals/" + approval + "/decision"),
                "{\"status\":\"REJECTED\",\"approverName\":\"x\"}").with(owner.auth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_DECIDED"));
        String quote = id(mvc.perform(json(post("/api/v1/jobs/" + job + "/approvals"), "{\"kind\":\"QUOTATION\"}")
                .with(owner.auth())).andExpect(jsonPath("$.status").value("PENDING")));
        mvc.perform(json(post("/api/v1/jobs/" + job + "/approvals/" + quote + "/decision"),
                "{\"status\":\"REJECTED\",\"approverName\":\"المدير المالي\",\"notes\":\"السعر مرتفع\"}").with(owner.auth()))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decidedAt").isNotEmpty());
        mvc.perform(get("/api/v1/jobs/" + job + "/approvals").with(owner.auth())).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void techniciansOnlyOpenFilesOfTheirOwnWork() throws Exception {
        Session owner = Api.register(mvc);
        Session tech = Invites.invite(mvc, emails, owner, "TECHNICIAN");
        String customer = id(mvc.perform(json(post("/api/v1/contacts"), """
                {"type":"CLIENT","name":{"first":"Omar"},"phone":"+20223456789"}
                """).with(owner.auth())));
        String site = id(mvc.perform(json(post("/api/v1/locations"), """
                {"contactId":"%s","type":"OFFICE","name":"HQ","address":{"city":"Giza","country":"EG"}}
                """.formatted(customer)).with(owner.auth())));
        String mine = id(mvc.perform(json(post("/api/v1/jobs"), """
                {"contactId":"%s","locationId":"%s","title":"Mine","assigneeUserIds":["%s"]}
                """.formatted(customer, site, tech.userId())).with(owner.auth())));
        String other = id(mvc.perform(json(post("/api/v1/jobs"), """
                {"contactId":"%s","locationId":"%s","title":"Other"}
                """.formatted(customer, site)).with(owner.auth())));

        String onMine = upload(owner, "INSPECTION");
        String onOther = upload(owner, "INSPECTION");
        String unattached = upload(owner, "OTHER");
        mvc.perform(json(post("/api/v1/jobs/" + mine + "/files"), "{\"fileIds\":[\"%s\"]}".formatted(onMine))
                .with(owner.auth())).andExpect(status().isOk());
        mvc.perform(json(post("/api/v1/jobs/" + other + "/files"), "{\"fileIds\":[\"%s\"]}".formatted(onOther))
                .with(owner.auth())).andExpect(status().isOk());

        mvc.perform(get("/api/v1/files/" + onMine).with(tech.auth())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/files/" + onMine + "/url").with(tech.auth())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/files/" + onOther).with(tech.auth())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/files/" + onOther + "/url").with(tech.auth())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/files/" + unattached).with(tech.auth())).andExpect(status().isNotFound());
        String own = upload(tech, "DURING");
        mvc.perform(get("/api/v1/files/" + own).with(tech.auth())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/files/" + onOther).with(owner.auth())).andExpect(status().isOk()); // files:read
        mvc.perform(delete("/api/v1/files/" + unattached).with(owner.auth())).andExpect(status().isNoContent());
    }

    private String upload(Session s, String purpose) throws Exception {
        BufferedImage image = new BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        String body = mvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("files", "photo.png", "image/png", png.toByteArray()))
                        .param("kind", "IMAGE").param("purpose", purpose).with(s.auth()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.files[0].id");
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
