package net.java21.data2flow.contracts.idempotency;

import net.java21.data2flow.contracts.error.BusinessException;
import net.java21.data2flow.contracts.error.CommonErrorCode;
import net.java21.data2flow.contracts.identity.DataflowHeaders;
import net.java21.data2flow.contracts.support.MutableClock;
import net.java21.data2flow.contracts.web.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** OPS-12.03·BR-OPS-20: Idempotency-Key (24시간, 조직+사용자+경로, 같은 본문은 처음 응답 재생, 다른 본문은 409) */
@WebMvcTest(controllers = IdempotencyWebTest.CommandController.class)
@Import(IdempotencyWebTest.CommandController.class)
@TestPropertySource(properties = "data2flow.idempotency.max-body-bytes=1024")
class IdempotencyWebTest {

    private static final String COMMAND = "{\"capability\":\"power\",\"command\":\"on\"}";
    private static final String ROUTE = "POST /core/devices/{device-id}/commands";

    @Autowired
    MockMvc mvc;
    @Autowired
    CommandController controller;
    @Autowired
    MutableClock clock;
    @Autowired
    InMemoryIdempotencyStore store;

    @BeforeEach
    void reset() {
        controller.executions.set(0);
        store.deleteExpired(Instant.MAX);
    }

    @Test
    @DisplayName("[OPS-12.03][AT-OPS-25.3][TC-OPS-125] 같은 키·같은 본문 두 번 → 같은 응답(201·Location·본문), 실행 1회")
    void replaysFirstResponse() throws Exception {
        // given
        mvc.perform(command("1042", "k-1", COMMAND))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/core/commands/1"))
                .andExpect(jsonPath("$.response.commandId").value("1"));
        // when & then
        clock.advance(Duration.ofHours(23));
        mvc.perform(command("1042", "k-1", COMMAND))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/core/commands/1"))
                .andExpect(jsonPath("$.header.resultCode").value("SUCCESS"))
                .andExpect(jsonPath("$.response.commandId").value("1"));
        assertThat(controller.executions).hasValue(1);
    }

    @Test
    @DisplayName("[OPS-12.03][AT-OPS-25.4] 같은 키·다른 본문, 같은 키·다른 기기 경로 → 409 IDEMPOTENCY_KEY_REUSED")
    void differentRequestSameKey() throws Exception {
        mvc.perform(command("1042", "k-2", COMMAND)).andExpect(status().isCreated());
        mvc.perform(command("1042", "k-2", "{\"capability\":\"power\",\"command\":\"off\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.header.resultCode").value("IDEMPOTENCY_KEY_REUSED"))
                .andExpect(jsonPath("$.header.resultMessage").value("같은 요청 키가 다른 내용으로 사용되었습니다"));
        mvc.perform(command("2000", "k-2", COMMAND))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.header.resultCode").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(controller.executions).hasValue(1);
    }

    @Test
    @DisplayName("[OPS-12.03][BR-OPS-20] 첫 요청이 처리 중이면 409 IDEMPOTENCY_CONFLICT + Retry-After, 60초 넘게 멈춘 키는 넘겨받는다")
    void inProgress() throws Exception {
        // given: 다른 파드가 같은 요청을 잡고 아직 끝내지 않았다
        MockHttpServletRequest same = new MockHttpServletRequest("POST", "/core/devices/1042/commands");
        String hash = IdempotencyInterceptor.hash(same, COMMAND.getBytes(StandardCharsets.UTF_8));
        Instant now = clock.instant();
        store.claim(new IdempotencyScope(1, 7, ROUTE, "k-3"), hash, now, now.plus(Duration.ofHours(24)), now);
        // when & then
        mvc.perform(command("1042", "k-3", COMMAND))
                .andExpect(status().isConflict())
                .andExpect(header().string("Retry-After", "2"))
                .andExpect(jsonPath("$.header.resultCode").value("IDEMPOTENCY_CONFLICT"));
        assertThat(controller.executions).hasValue(0);

        clock.advance(Duration.ofSeconds(61));
        mvc.perform(command("1042", "k-3", COMMAND)).andExpect(status().isCreated());
        assertThat(controller.executions).hasValue(1);
    }

    @Test
    @DisplayName("[OPS-12.03][BR-OPS-20] 키 범위는 조직+사용자: 다른 사용자가 같은 키를 쓰면 따로 실행된다. 24시간 뒤 키는 새로 쓴다")
    void scopeAndExpiry() throws Exception {
        mvc.perform(command("1042", "k-4", COMMAND)).andExpect(status().isCreated());
        mvc.perform(command("1042", "k-4", COMMAND, "8")).andExpect(status().isCreated());
        assertThat(controller.executions).hasValue(2);

        clock.advance(Duration.ofHours(24));
        mvc.perform(command("1042", "k-4", COMMAND)).andExpect(status().isCreated());
        assertThat(controller.executions).hasValue(3);
    }

    @Test
    @DisplayName("[OPS-12.03] 처음 응답이 4xx면 저장하지 않고 키를 풀어 같은 키로 다시 시도할 수 있다")
    void failuresAreNotStored() throws Exception {
        controller.failNext.set(true);
        mvc.perform(command("1042", "k-5", COMMAND))
                .andExpect(status().isBadRequest());
        mvc.perform(command("1042", "k-5", COMMAND)).andExpect(status().isCreated());
        assertThat(controller.executions).hasValue(2);
    }

    @Test
    @DisplayName("[OPS-12.03] 필수 엔드포인트에 키가 없거나 형식이 틀리면 400 INVALID_REQUEST(errors[0].field=Idempotency-Key)")
    void keyValidation() throws Exception {
        mvc.perform(post("/core/devices/1042/commands").contentType(MediaType.APPLICATION_JSON).content(COMMAND)
                        .header(DataflowHeaders.USER_ID, "7").header(DataflowHeaders.ORG_ID, "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"))
                .andExpect(jsonPath("$.errors[0].code").value("NotBlank"));
        mvc.perform(command("1042", "x".repeat(65), COMMAND))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("Pattern"));
        mvc.perform(command("1042", "bad key", COMMAND)).andExpect(status().isBadRequest());
        assertThat(controller.executions).hasValue(0);
    }

    @Test
    @DisplayName("[OPS-12.03] 선택 엔드포인트는 키가 없으면 매번 실행, @Idempotent가 없는 엔드포인트는 키를 무시한다")
    void optionalAndPlain() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/core/jobs").contentType(MediaType.APPLICATION_JSON).content("{}")
                    .header(DataflowHeaders.USER_ID, "7").header(DataflowHeaders.ORG_ID, "1")).andExpect(status().isAccepted());
            mvc.perform(post("/core/plain").contentType(MediaType.APPLICATION_JSON).content("{}")
                    .header(DataflowHeaders.IDEMPOTENCY_KEY, "k-6")
                    .header(DataflowHeaders.USER_ID, "7").header(DataflowHeaders.ORG_ID, "1")).andExpect(status().isOk());
        }
        mvc.perform(post("/core/jobs").contentType(MediaType.APPLICATION_JSON).content("{}")
                .header(DataflowHeaders.IDEMPOTENCY_KEY, "k-7")
                .header(DataflowHeaders.USER_ID, "7").header(DataflowHeaders.ORG_ID, "1")).andExpect(status().isAccepted());
        mvc.perform(post("/core/jobs").contentType(MediaType.APPLICATION_JSON).content("{}")
                .header(DataflowHeaders.IDEMPOTENCY_KEY, "k-7")
                .header(DataflowHeaders.USER_ID, "7").header(DataflowHeaders.ORG_ID, "1")).andExpect(status().isAccepted());
        assertThat(controller.executions).hasValue(5);
    }

    @Test
    @DisplayName("[OPS-12.03] 신원 없는 내부 호출도 키를 쓸 수 있고, 미리 읽는 본문 상한을 넘으면 413 PAYLOAD_TOO_LARGE")
    void internalAndLargeBody() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/internal/core/jobs").contentType(MediaType.APPLICATION_JSON).content("{}")
                    .header(DataflowHeaders.IDEMPOTENCY_KEY, "k-8")).andExpect(status().isAccepted());
        }
        assertThat(controller.executions).hasValue(1);
        mvc.perform(command("1042", "k-9", "{\"a\":\"" + "x".repeat(2000) + "\"}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.header.resultCode").value("PAYLOAD_TOO_LARGE"));
    }

    private static MockHttpServletRequestBuilder command(String deviceId, String key, String body) {
        return command(deviceId, key, body, "7");
    }

    private static MockHttpServletRequestBuilder command(String deviceId, String key, String body, String userId) {
        return post("/core/devices/{device-id}/commands", deviceId).contentType(MediaType.APPLICATION_JSON).content(body)
                .header(DataflowHeaders.IDEMPOTENCY_KEY, key)
                .header(DataflowHeaders.USER_ID, userId).header(DataflowHeaders.ORG_ID, "1");
    }

    @SpringBootApplication
    static class TestApp {

        @Bean
        MutableClock clock() {
            return MutableClock.atUtc("2026-10-03T00:00:00Z");
        }

        @Bean
        InMemoryIdempotencyStore idempotencyStore() {
            return new InMemoryIdempotencyStore();
        }
    }

    @RestController
    static class CommandController {

        final AtomicInteger executions = new AtomicInteger();
        final java.util.concurrent.atomic.AtomicBoolean failNext = new java.util.concurrent.atomic.AtomicBoolean();

        @PostMapping("/core/devices/{device-id}/commands")
        @Idempotent(required = true)
        ResponseEntity<ApiResponse<Map<String, String>>> send(@PathVariable("device-id") String deviceId,
                                                             @RequestBody Map<String, Object> body) {
            int n = executions.incrementAndGet();
            if (failNext.getAndSet(false)) {
                throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
            }
            return ResponseEntity.created(URI.create("/api/v1/core/commands/" + n))
                    .body(ApiResponse.success(Map.of("commandId", Integer.toString(n), "deviceId", deviceId)));
        }

        @PostMapping({"/core/jobs", "/internal/core/jobs"})
        @Idempotent
        ResponseEntity<ApiResponse<String>> job(@RequestBody Map<String, Object> body) {
            executions.incrementAndGet();
            return ResponseEntity.accepted().body(ApiResponse.success("queued"));
        }

        @PostMapping("/core/plain")
        ApiResponse<String> plain(@RequestBody Map<String, Object> body) {
            executions.incrementAndGet();
            return ApiResponse.success("ok");
        }
    }
}
