package com.crmplatform.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmplatform.support.Api;
import com.crmplatform.support.Api.Session;
import com.crmplatform.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class FilesIT {

    @Autowired
    MockMvc mvc;

    @Test
    void imagesAreReencodedToWebpAndDownloadableViaPresignedUrl() throws Exception { // TS-FIL-01, TS-FIL-05
        Session s = Api.register(mvc);
        String json = mvc.perform(multipart("/api/v1/files").file(new MockMultipartFile("files", "photo.png",
                        "image/png", png(3000, 1500))).param("kind", "IMAGE").with(s.auth()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.files[0].name").value("photo.webp"))
                .andExpect(jsonPath("$.files[0].contentType").value("image/webp"))
                .andExpect(jsonPath("$.files[0].attached").value(false))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.files[0].id");

        String urlJson = mvc.perform(get("/api/v1/files/" + id + "/url").with(s.auth()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        HttpResponse<byte[]> download = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(JsonPath.read(urlJson, "$.url"))).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(download.statusCode()).isEqualTo(200);
        BufferedImage stored = ImageIO.read(new java.io.ByteArrayInputStream(download.body()));
        assertThat(stored.getWidth()).isEqualTo(2048); // downscaled long edge

        Session other = Api.register(mvc);
        mvc.perform(get("/api/v1/files/" + id).with(other.auth())).andExpect(status().isNotFound());
    }

    @Test
    void documentsAreSniffedByContent() throws Exception { // TS-FIL-03
        Session s = Api.register(mvc);
        mvc.perform(multipart("/api/v1/files").file(new MockMultipartFile("files", "quote.pdf", "application/pdf",
                        "%PDF-1.4\n%âãÏÓ\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.ISO_8859_1)))
                        .param("kind", "DOCUMENT").with(s.auth()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.files[0].contentType").value("application/pdf"));

        byte[] executable = new byte[] {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0, (byte) 0xFF, (byte) 0xFF};
        mvc.perform(multipart("/api/v1/files").file(new MockMultipartFile("files", "invoice.pdf", "application/pdf",
                        executable)).param("kind", "DOCUMENT").with(s.auth()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void unattachedFilesCanBeDeletedByUploader() throws Exception { // FR-FIL-03
        Session s = Api.register(mvc);
        String json = mvc.perform(multipart("/api/v1/files").file(new MockMultipartFile("files", "a.png", "image/png",
                png(10, 10))).param("kind", "IMAGE").with(s.auth())).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.files[0].id");
        mvc.perform(delete("/api/v1/files/" + id).with(s.auth())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/files/" + id).with(s.auth())).andExpect(status().isNotFound());
    }

    private static byte[] png(int w, int h) throws Exception {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
