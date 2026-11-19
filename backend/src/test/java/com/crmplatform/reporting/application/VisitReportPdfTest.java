package com.crmplatform.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.crmplatform.operations.VisitReports.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class VisitReportPdfTest {

    @Test
    void userTextTakesTheDirectionOfItsFirstStrongCharacter() {
        var inEnglishDoc = new VisitReportPdf.Directions("ltr");
        assertThat(inEnglishDoc.of("الكاميرا رقم 3 لا تعمل")).isEqualTo("rtl");
        assertThat(inEnglishDoc.of("3 كاميرات")).isEqualTo("rtl"); // digits are not strong
        assertThat(inEnglishDoc.of("Cat6 كابل")).isEqualTo("ltr");
        assertThat(inEnglishDoc.of("+20 123")).isEqualTo("ltr"); // no strong char → document
        assertThat(new VisitReportPdf.Directions("rtl").of("123")).isEqualTo("rtl");
        assertThat(inEnglishDoc.of(null)).isEqualTo("ltr");
    }

    @Test
    void imagesAreBoundedJpegDataUrisAndBrokenImagesAreSkipped() throws Exception {
        BufferedImage big = new BufferedImage(2400, 1200, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(big, "png", png);
        String uri = VisitReportPdf.dataUri(new Image("image/png", png.toByteArray()), 1000);
        assertThat(uri).startsWith("data:image/jpeg;base64,");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(
                Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1))));
        assertThat(decoded.getWidth()).isEqualTo(1000);
        assertThat(decoded.getHeight()).isEqualTo(500);

        assertThat(VisitReportPdf.dataUri(new Image("image/webp", new byte[] {1, 2, 3}), 1000)).isNull();
    }
}
