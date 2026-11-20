package com.crmplatform.reporting.application;

import com.openhtmltopdf.bidi.support.ICUBidiReorderer;
import com.openhtmltopdf.bidi.support.ICUBidiSplitter;
import com.openhtmltopdf.bidi.support.ICUBreakers;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.TextDirection;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * XHTML → PDF with real Arabic support (D15): ICU4J bidi splitting/reordering and Arabic shaping,
 * ICU line breaking, and the embedded IBM Plex Sans Arabic font (OFL-1.1, covers Arabic and Latin).
 * The renderer may load nothing from outside: only {@code data:} URIs (embedded images) are allowed,
 * so a document can never make the server fetch a URL.
 */
@Component
public class PdfRenderer {

    static final String FONT_FAMILY = "Plex Arabic";

    private static final byte[] REGULAR = font("IBMPlexSansArabic-Regular.ttf");
    private static final byte[] BOLD = font("IBMPlexSansArabic-Bold.ttf");

    public byte[] render(String xhtml, Locale locale) {
        boolean rtl = "ar".equals(locale.getLanguage());
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.useFont(() -> new ByteArrayInputStream(REGULAR), FONT_FAMILY, 400, FontStyle.NORMAL, true);
        builder.useFont(() -> new ByteArrayInputStream(BOLD), FONT_FAMILY, 700, FontStyle.NORMAL, true);
        builder.useUnicodeBidiSplitter(new ICUBidiSplitter.ICUBidiSplitterFactory());
        builder.useUnicodeBidiReorderer(new ICUBidiReorderer());
        builder.useUnicodeLineBreaker(new ICUBreakers.ICULineBreaker(locale));
        builder.defaultTextDirection(rtl ? TextDirection.RTL : TextDirection.LTR);
        builder.useExternalResourceAccessControl((uri, type) -> uri != null && uri.startsWith("data:"),
                ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI);
        builder.useExternalResourceAccessControl((uri, type) -> uri != null && uri.startsWith("data:"),
                ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI);
        builder.withHtmlContent(xhtml, null);
        builder.toStream(out);
        try {
            builder.run();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static byte[] font(String name) {
        try (InputStream in = PdfRenderer.class.getResourceAsStream("/fonts/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing font resource " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
