package net.java21.data2flow.contracts.idempotency;

import net.java21.data2flow.contracts.identity.DataflowHeaders;
import net.java21.data2flow.contracts.web.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OPS-12.03: multipart·form 본문에 Idempotency-Key를 붙여도 500이 나지 않는다(실제 Tomcat). 예전에는 멱등 필터가 원문 스트림을 먼저
 * 읽어 버려 Tomcat이 파트를 읽지 못했다(평면도 올리기 {@code PUT /core/spaces/{id}/floorplan} 500).
 * {@link Idempotent} 엔드포인트의 multipart 해시는 경계 문자열이 아니라 파트(이름·파일 이름·형식·내용)로 계산한다.
 */
@SpringBootTest(classes = IdempotencyMultipartTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "data2flow.idempotency.max-body-bytes=300")
class IdempotencyMultipartTest {

    @LocalServerPort
    int port;
    @Autowired
    UploadController controller;
    @Autowired
    InMemoryIdempotencyStore store;

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @BeforeEach
    void reset() {
        controller.executions.set(0);
        store.deleteExpired(Instant.MAX);
    }

    @Test
    @DisplayName("[OPS-12.03][DEV-01.03] @Idempotent가 아닌 multipart PUT(평면도)에 키를 붙여도 파일이 그대로 도착한다(500 아님, 본문 상한 무관)")
    void plainMultipartIgnoresKey() throws Exception {
        for (int size : new int[]{3, 3, 1000}) {
            byte[] image = "x".repeat(size).getBytes(StandardCharsets.UTF_8);
            HttpResponse<String> res = send("PUT", "/core/spaces/7/floorplan?scaleMPerPx=0.05", "k-plan", image, "p.png");
            assertThat(res.statusCode()).isEqualTo(200);
            assertThat(res.body()).contains("\"size\":\"" + size + "\"").contains("\"scale\":\"0.05\"");
        }
        assertThat(controller.executions).hasValue(3);
    }

    @Test
    @DisplayName("[OPS-12.03][AT-OPS-25.3] @Idempotent multipart: 같은 키·같은 파일은 경계 문자열이 달라도 처음 응답 재생, 다른 파일은 409")
    void idempotentMultipart() throws Exception {
        byte[] file = "a\n1\n".getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> first = send("POST", "/core/imports", "k-imp", file, "a.csv");
        HttpResponse<String> again = send("POST", "/core/imports", "k-imp", file, "a.csv");
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(again.statusCode()).isEqualTo(200);
        assertThat(again.body()).isEqualTo(first.body());
        assertThat(controller.executions).hasValue(1);

        HttpResponse<String> other = send("POST", "/core/imports", "k-imp", "c\n3\n".getBytes(StandardCharsets.UTF_8), "a.csv");
        assertThat(other.statusCode()).isEqualTo(409);
        assertThat(other.body()).contains("IDEMPOTENCY_KEY_REUSED");
        assertThat(controller.executions).hasValue(1);
    }

    @Test
    @DisplayName("[OPS-12.03] form 본문(x-www-form-urlencoded)에 키를 붙여도 파라미터가 그대로 도착한다")
    void formBody() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/core/forms"))
                .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .header(DataflowHeaders.IDEMPOTENCY_KEY, "k-form")
                .header(DataflowHeaders.USER_ID, "7").header(DataflowHeaders.ORG_ID, "1")
                .POST(HttpRequest.BodyPublishers.ofString("name=hello")).build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("hello");
    }

    private HttpResponse<String> send(String method, String path, String key, byte[] file, String filename)
            throws IOException, InterruptedException {
        String boundary = "b-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename
                + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(file);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header(DataflowHeaders.IDEMPOTENCY_KEY, key)
                .header(DataflowHeaders.USER_ID, "7").header(DataflowHeaders.ORG_ID, "1")
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(UploadController.class)
    static class App {

        @Bean
        InMemoryIdempotencyStore idempotencyStore() {
            return new InMemoryIdempotencyStore();
        }
    }

    @RestController
    static class UploadController {

        final AtomicInteger executions = new AtomicInteger();

        @PutMapping(value = "/core/spaces/{space-id}/floorplan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        ApiResponse<Map<String, String>> floorplan(@PathVariable("space-id") long spaceId,
                                                   @RequestPart(value = "file", required = false) MultipartFile file,
                                                   @RequestParam(required = false) String scaleMPerPx) {
            executions.incrementAndGet();
            return ApiResponse.success(Map.of("size", Long.toString(file == null ? -1 : file.getSize()),
                    "scale", String.valueOf(scaleMPerPx)));
        }

        @PostMapping(value = "/core/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @Idempotent(required = true)
        ApiResponse<Map<String, String>> imports(@RequestPart("file") MultipartFile file) {
            int n = executions.incrementAndGet();
            return ApiResponse.success(Map.of("importId", Integer.toString(n), "size", Long.toString(file.getSize())));
        }

        @PostMapping(value = "/core/forms", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        ApiResponse<String> form(@RequestParam("name") String name) {
            return ApiResponse.success(name);
        }
    }
}
