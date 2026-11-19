package com.crmplatform.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.crmplatform.support.TestBeans;
import com.crmplatform.support.TestcontainersConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Response compression is a connector setting, so it is verified against a real embedded server. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, TestBeans.class})
class CompressionIT {

    @LocalServerPort
    int port;

    @Test
    void largeJsonResponsesAreGzipped() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        URI uri = URI.create("http://localhost:" + port + "/v3/api-docs");

        HttpResponse<byte[]> gzipped = client.send(HttpRequest.newBuilder(uri).header("Accept-Encoding", "gzip").build(),
                HttpResponse.BodyHandlers.ofByteArray());
        HttpResponse<byte[]> plain = client.send(HttpRequest.newBuilder(uri).build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertThat(gzipped.statusCode()).isEqualTo(200);
        assertThat(gzipped.headers().firstValue("Content-Encoding")).hasValue("gzip");
        assertThat(plain.headers().firstValue("Content-Encoding")).isEmpty();
        // measured, not assumed: the compressed body is a fraction of the plain one
        assertThat(gzipped.body().length).isLessThan(plain.body().length / 3);
        System.out.printf("api-docs: %d bytes plain, %d bytes gzip%n", plain.body().length, gzipped.body().length);
    }
}
