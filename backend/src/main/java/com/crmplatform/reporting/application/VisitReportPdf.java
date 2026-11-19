package com.crmplatform.reporting.application;

import com.crmplatform.crm.Contacts;
import com.crmplatform.identity.WorkspaceMembers;
import com.crmplatform.identity.WorkspaceMembers.WorkspaceProfile;
import com.crmplatform.operations.VisitReports;
import com.crmplatform.operations.VisitReports.Image;
import com.crmplatform.operations.VisitReports.Photo;
import com.crmplatform.operations.VisitReports.VisitReportDocument;
import com.crmplatform.shared.i18n.Languages;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DecimalStyle;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * The printable technical visit report (R12): Arabic (right-to-left) or English, in the workspace
 * time zone, with the visit's photos and the customer's signature embedded. Text comes from the
 * records exactly as entered and is HTML-escaped by Thymeleaf. Internal data (costs, internal job
 * notes) is never printed.
 */
@Service
public class VisitReportPdf {

    /** Photos per report: keeps the file small and the render fast. */
    static final int MAX_PHOTOS = 12;
    static final int PHOTO_EDGE = 1000;
    static final int SIGNATURE_EDGE = 600;

    private static final Logger log = LoggerFactory.getLogger(VisitReportPdf.class);
    private static final List<String> PURPOSE_ORDER = List.of("BEFORE", "DURING", "AFTER", "INSPECTION");

    private final VisitReports visits;
    private final Contacts contacts;
    private final WorkspaceMembers members;
    private final TemplateEngine templates;
    private final MessageSource messages;
    private final PdfRenderer renderer;
    private final Clock clock;

    public VisitReportPdf(VisitReports visits, Contacts contacts, WorkspaceMembers members, TemplateEngine templates,
            MessageSource messages, PdfRenderer renderer, Clock clock) {
        this.visits = visits;
        this.contacts = contacts;
        this.members = members;
        this.templates = templates;
        this.messages = messages;
        this.renderer = renderer;
        this.clock = clock;
    }

    public record Pdf(String fileName, byte[] content) {
    }

    @Transactional(readOnly = true)
    public Pdf render(UUID visitId, String language) {
        VisitReportDocument doc = visits.document(visitId); // access check (404)
        WorkspaceProfile ws = members.currentWorkspace();
        String lang = Languages.isSupported(language) ? language : Languages.normalize(ws.defaultLanguage())
                .orElse(Languages.DEFAULT);
        Locale locale = Locale.forLanguageTag(Languages.ARABIC.equals(lang) ? "ar-EG" : "en-EG");
        ZoneId zone = zone(ws.timeZone());
        Text t = new Text(messages, locale, zone);

        Map<String, Object> model = new java.util.HashMap<>();
        model.put("lang", lang);
        String dir = Languages.ARABIC.equals(lang) ? "rtl" : "ltr";
        model.put("dir", dir);
        model.put("bidi", new Directions(dir));
        model.put("workspace", ws.name());
        model.put("generatedAt", t.dateTime(Instant.now(clock)));

        Optional<Contacts.ContactSummary> customer = contacts.find(doc.contactId());
        model.put("customerName", customer.map(Contacts.ContactSummary::fullName).orElse(""));
        model.put("customerPhone", customer.map(c -> c.phone() != null ? c.phone() : c.mobile()).orElse(null));
        Optional<Contacts.LocationSummary> site = contacts.location(doc.locationId());
        model.put("siteName", site.map(Contacts.LocationSummary::name).orElse(""));
        model.put("siteAddress", site.map(s -> join(s.addressLine1(), s.city())).orElse(null));

        model.put("jobTitle", doc.jobTitle());
        model.put("jobType", t.label("jobType", doc.jobType()));
        model.put("priority", t.label("priority", doc.jobPriority()));
        model.put("reportedIssue", blank(doc.reportedIssue()));
        model.put("scheduled", t.range(doc.scheduledStart(), doc.scheduledEnd()));
        model.put("actual", doc.actualStart() == null ? null : t.range(doc.actualStart(), doc.actualEnd()));
        model.put("visitStatus", t.label("visitStatus", doc.visitStatus()));
        model.put("outcome", t.label("outcome", doc.outcome()));
        model.put("technicians", members.findAll(doc.assigneeUserIds()).values().stream()
                .map(WorkspaceMembers.MemberSummary::fullName).sorted().toList());

        List<Section> sections = new ArrayList<>();
        VisitReports.Report r = doc.report();
        if (r != null) {
            add(sections, t.text("report.visit.reportedProblem"), r.reportedProblem());
            add(sections, t.text("report.visit.findings"), r.findings());
            add(sections, t.text("report.visit.diagnosis"), r.diagnosis());
            add(sections, t.text("report.visit.workPerformed"), r.workPerformed());
            add(sections, t.text("report.visit.recommendations"), r.recommendations());
            add(sections, t.text("report.visit.notes"), r.notes());
        }
        model.put("sections", sections);
        model.put("materials", doc.materials().stream()
                .map(m -> new MaterialRow(m.name(), t.number(m.quantity()), m.unit())).toList());
        model.put("photos", photos(doc, t));

        if (r != null && r.customerName() != null) {
            String signature = r.signatureFileId() == null ? null
                    : visits.image(visitId, r.signatureFileId()).map(i -> dataUri(i, SIGNATURE_EDGE)).orElse(null);
            model.put("confirmation", new Confirmation(r.customerName(), t.dateTime(r.customerConfirmedAt()),
                    signature));
        }

        String html = templates.process("reports/visit-report", new Context(locale, model));
        byte[] pdf = renderer.render(html, locale);
        String day = DateTimeFormatter.ISO_LOCAL_DATE.format(
                (doc.actualStart() != null ? doc.actualStart() : doc.scheduledStart()).atZone(zone));
        return new Pdf("visit-report-" + day + ".pdf", pdf);
    }

    /** Photos in rows of two (tables lay out reliably in both directions). */
    private List<List<PhotoCell>> photos(VisitReportDocument doc, Text t) {
        List<Photo> ordered = doc.photos().stream()
                .sorted(Comparator.comparingInt(p -> {
                    int i = p.purpose() == null ? -1 : PURPOSE_ORDER.indexOf(p.purpose());
                    return i < 0 ? PURPOSE_ORDER.size() : i;
                })).limit(MAX_PHOTOS).toList();
        List<PhotoCell> cells = new ArrayList<>();
        for (Photo p : ordered) {
            visits.image(doc.visitId(), p.fileId()).map(i -> dataUri(i, PHOTO_EDGE))
                    .ifPresent(src -> cells.add(new PhotoCell(src,
                            t.label("purpose", p.purpose() == null ? "OTHER" : p.purpose()))));
        }
        List<List<PhotoCell>> rows = new ArrayList<>();
        for (int i = 0; i < cells.size(); i += 2) {
            rows.add(List.copyOf(cells.subList(i, Math.min(i + 2, cells.size()))));
        }
        return rows;
    }

    /** Re-encodes a stored image (WebP) as a bounded JPEG data URI the PDF engine can embed. */
    static String dataUri(Image image, int maxEdge) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(image.bytes()));
            if (source == null) {
                return null;
            }
            double scale = Math.min(1.0, (double) maxEdge / Math.max(source.getWidth(), source.getHeight()));
            int w = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int h = Math.max(1, (int) Math.round(source.getHeight() * scale));
            BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setColor(Color.WHITE); // transparent signatures on white
                g.fillRect(0, 0, w, h);
                g.drawImage(source, 0, 0, w, h, null);
            } finally {
                g.dispose();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.82f);
                writer.write(null, new IIOImage(rgb, null, null), param);
            } finally {
                writer.dispose();
            }
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException | RuntimeException e) {
            log.warn("Skipping an image in the visit report: {}", e.getMessage());
            return null;
        }
    }

    private static void add(List<Section> sections, String label, String text) {
        if (text != null && !text.isBlank()) {
            sections.add(new Section(label, text.strip()));
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static String join(String a, String b) {
        String x = blank(a);
        String y = blank(b);
        return x == null ? y : y == null ? x : x + "، " + y;
    }

    private static ZoneId zone(String id) {
        try {
            return ZoneId.of(id);
        } catch (RuntimeException e) {
            return ZoneId.of("Africa/Cairo");
        }
    }

    public record Section(String label, String text) {
    }

    public record MaterialRow(String name, String quantity, String unit) {
    }

    public record PhotoCell(String src, String caption) {
    }

    public record Confirmation(String name, String at, String signatureSrc) {
    }

    /**
     * Direction of user-entered text, like HTML {@code dir="auto"} (which the PDF engine lacks): the
     * first strong character decides, so Arabic text with numbers stays in order inside an English
     * document and vice versa. Text without strong characters follows the document.
     */
    public record Directions(String documentDir) {
        public String of(String text) {
            if (text != null) {
                for (int i = 0; i < text.length(); ) {
                    int cp = text.codePointAt(i);
                    byte d = Character.getDirectionality(cp);
                    if (d == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                            || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
                        return "rtl";
                    }
                    if (d == Character.DIRECTIONALITY_LEFT_TO_RIGHT) {
                        return "ltr";
                    }
                    i += Character.charCount(cp);
                }
            }
            return documentDir;
        }
    }

    /** Fixed text, labels and formatting in the document's language and the workspace time zone. */
    record Text(MessageSource messages, Locale locale, ZoneId zone) {

        String text(String key) {
            return messages.getMessage(key, null, key, locale);
        }

        String label(String prefix, String value) {
            return value == null ? null : messages.getMessage(prefix + "." + value, null, value, locale);
        }

        String dateTime(Instant at) {
            if (at == null) {
                return null;
            }
            String s = formatter(FormatStyle.MEDIUM, FormatStyle.SHORT).format(at.atZone(zone));
            // the JDK's ar pattern joins date and time with a Latin comma; Arabic text uses "،"
            return Languages.ARABIC.equals(locale.getLanguage()) ? s.replace(", ", "، ") : s;
        }

        String range(Instant start, Instant end) {
            if (start == null) {
                return null;
            }
            String from = dateTime(start);
            if (end == null) {
                return from;
            }
            boolean sameDay = start.atZone(zone).toLocalDate().equals(end.atZone(zone).toLocalDate());
            String to = sameDay ? DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
                    .withDecimalStyle(DecimalStyle.of(locale)).format(end.atZone(zone)) : dateTime(end);
            return from + " – " + to;
        }

        String number(BigDecimal n) {
            if (n == null) {
                return "";
            }
            NumberFormat f = NumberFormat.getNumberInstance(locale);
            f.setMaximumFractionDigits(3);
            return f.format(n.stripTrailingZeros());
        }

        private DateTimeFormatter formatter(FormatStyle date, FormatStyle time) {
            return DateTimeFormatter.ofLocalizedDateTime(date, time).withLocale(locale)
                    .withDecimalStyle(DecimalStyle.of(locale));
        }
    }
}
