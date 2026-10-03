package net.java21.data2flow.contracts.audit;

import net.java21.data2flow.contracts.identity.CurrentUser;
import net.java21.data2flow.contracts.secret.Secret;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** IAM-06.01: 감사 기록 공통 모양과 비밀값 가림 */
class AuditEventTest {

    private static final Instant AT = Instant.parse("2026-10-03T00:00:00Z");

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("[IAM-06.01][AT-IAM-13.3] 소스 비밀번호 변경 기록의 변경 전후 값은 ***로 가려진다")
    void secretsAreMasked() {
        // given & when
        AuditEvent event = AuditEvent.builder(1, "SOURCE_UPDATED")
                .occurredAt(AT)
                .actor(new CurrentUser(7, 1))
                .target("SOURCE", "12")
                .detail("password", Map.of("before", "old-pass", "after", "new-pass"))
                .detail("brokerUrl", "mqtts://ingest:s3cr3t@broker.example:8883")
                .detail("apiKey", Secret.of("sk-live-123"))
                .detail("name", "1층 게이트웨이")
                .build();
        // then
        assertThat(event.detail()).containsEntry("password", "***")
                .containsEntry("apiKey", "***")
                .containsEntry("brokerUrl", "mqtts://ingest:***@broker.example:8883")
                .containsEntry("name", "1층 게이트웨이");
        assertThat(event.detail().toString()).doesNotContain("old-pass", "new-pass", "s3cr3t", "sk-live-123");
    }

    @Test
    @DisplayName("[IAM-06.04][AT-IAM-13.2] 플로우가 낸 제어 명령 감사는 actor_type=FLOW와 원인(flowId·버전·노드·트리거 메시지)을 갖는다")
    void flowCause() {
        MDC.put("requestId", "req-1");
        AuditEvent event = AuditEvent.builder(1, "DEVICE_COMMAND_SENT")
                .actor(AuditActorType.FLOW, "88", "에어컨 자동 제어")
                .cause(new AuditCause("88", 3, "node-7", "msg-42"))
                .target("DEVICE", "1042")
                .ip("10.0.0.1").userAgent("ua")
                .build();
        assertThat(event.actorType()).isEqualTo(AuditActorType.FLOW);
        assertThat(event.cause()).isEqualTo(new AuditCause("88", 3, "node-7", "msg-42"));
        assertThat(event.requestId()).isEqualTo("req-1");
        assertThat(event.result()).isEqualTo(AuditResult.SUCCESS);
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.detail()).isNull();
    }

    @Test
    @DisplayName("[IAM-06.01][BR-IAM-01] 조직 없는 기록, UPPER_SNAKE가 아닌 action은 만들 수 없고 긴 값은 열 길이에 맞게 자른다")
    void validation() {
        assertThatThrownBy(() -> AuditEvent.builder(0, "USER_LOGGED_IN").build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuditEvent.builder(1, "userLoggedIn").build()).isInstanceOf(IllegalArgumentException.class);
        AuditEvent event = AuditEvent.builder(1, "USER_LOGGED_IN")
                .actor(AuditActorType.USER, "7", "가".repeat(150))
                .userAgent("u".repeat(400))
                .requestId("r".repeat(80))
                .result(AuditResult.FAILURE)
                .build();
        assertThat(event.actorName()).hasSize(100);
        assertThat(event.userAgent()).hasSize(300);
        assertThat(event.requestId()).hasSize(64);
    }

    @Test
    @DisplayName("[IAM-06.01][IAM-05] 장기 토큰 요청의 행위는 토큰 ID를 detail에 남기고, JSON(API-IAM-39 본문)에 빈 값이 나가지 않는다")
    void tokenActorAndJson() {
        AuditEvent event = AuditEvent.builder(1, "ALARM_ACKNOWLEDGED")
                .occurredAt(AT)
                .actor(new CurrentUser(7, 1, 55L, Set.of("mcp:write")))
                .detail(Map.of("memo", "MCP(55)"))
                .requestId(null)
                .build();
        assertThat(event.detail()).containsEntry("accessTokenId", "55");
        String json = JsonMapper.builder().build().writeValueAsString(event);
        assertThat(json).contains("\"actorType\":\"USER\"", "\"actorId\":\"7\"", "\"occurredAt\"")
                .doesNotContain("\"cause\"", "\"requestId\"");
    }
}
