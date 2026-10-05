package net.java21.data2flow.contracts.message;

import net.java21.data2flow.contracts.message.event.AiQuotaExceeded;
import net.java21.data2flow.contracts.message.event.AnalyticsExportCompleted;
import net.java21.data2flow.contracts.message.event.AnalyticsRunStatusChanged;
import net.java21.data2flow.contracts.message.event.AnalyticsRunStatusChanged.Status;
import net.java21.data2flow.contracts.message.event.AnalyticsScheduleStopped;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** M6 분석(EVT-ANA-01~06)·AI(EVT-AIA-03) 이벤트: analytics(Python)가 보내는 모양 그대로, 하위 호환(모르는 값·필드) */
class AnalyticsEventsTest {

    private static final Instant T = Instant.parse("2026-10-05T00:00:42Z");
    private static final Clock CLOCK = Clock.fixed(T, ZoneOffset.UTC);
    private final MessageCodec codec = MessageCodec.create();

    @Test
    @DisplayName("ANA-06.02 EVT-ANA-01 analytics.run.{status}: 상태별 라우팅 키, progress·stage는 null이어도 싣고 errorCode·finishedAt은 있을 때만")
    void runStatus() {
        assertThat(EventType.analyticsRun(Status.SUCCEEDED)).isEqualTo(EventType.ANALYTICS_RUN_SUCCEEDED);
        assertThat(EventType.analyticsRun(Status.QUEUED).routingKey()).isEqualTo("analytics.run.queued");
        assertThat(EventType.ANALYTICS_RUN_CANCELLED.eventId()).isEqualTo("EVT-ANA-01");
        assertThatThrownBy(() -> EventType.analyticsRun(Status.UNKNOWN)).isInstanceOf(IllegalArgumentException.class);

        AnalyticsRunStatusChanged pending = new AnalyticsRunStatusChanged("41", "12", Status.PENDING, null,
                AnalyticsRunStatusChanged.Trigger.SCHEDULE, null, null, null);
        JsonNode tree = codec.toTree(DomainEvent.of(EventType.ANALYTICS_RUN_PENDING, 1, pending, null, CLOCK));
        assertThat(tree.get("payload").toString())
                .isEqualTo("{\"runId\":\"41\",\"analysisId\":\"12\",\"status\":\"PENDING\",\"progress\":null,\"trigger\":\"SCHEDULE\",\"stage\":null}");
        assertThat(pending.runIdAsLong()).isEqualTo(41L);
        assertThat(pending.analysisIdAsLong()).isEqualTo(12L);
        assertThat(Status.TIMEOUT.terminal()).isTrue();
        assertThat(Status.RUNNING.terminal()).isFalse();

        AnalyticsRunStatusChanged future = codec.mapper().readValue("""
                {"runId":"x","analysisId":"12","status":"PAUSED","trigger":"WEBHOOK","stage":"FIT","extra":1}""",
                AnalyticsRunStatusChanged.class);
        assertThat(future.status()).isEqualTo(Status.UNKNOWN);
        assertThat(future.trigger()).isEqualTo(AnalyticsRunStatusChanged.Trigger.UNKNOWN);
        assertThat(future.runIdAsLong()).isNull();
        assertThatThrownBy(() -> new AnalyticsRunStatusChanged(" ", "12", Status.PENDING, 0, AnalyticsRunStatusChanged.Trigger.API,
                null, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ANA-06.02 EVT-ANA-05·06: 문자열 ID를 숫자로, 요청자 없는 내보내기는 userId:null")
    void scheduleAndExport() {
        AnalyticsScheduleStopped stopped = new AnalyticsScheduleStopped("12", "7", 3);
        assertThat(stopped.analysisIdAsLong()).isEqualTo(12L);
        assertThat(stopped.ownerUserIdAsLong()).isEqualTo(7L);
        AnalyticsExportCompleted anonymous = new AnalyticsExportCompleted("9", null, "/bff/download/x", T, null);
        assertThat(codec.toTree(DomainEvent.of(EventType.ANALYTICS_EXPORT_COMPLETED, 1, anonymous, null, CLOCK)).get("payload").toString())
                .isEqualTo("{\"exportJobId\":\"9\",\"userId\":null,\"downloadUrl\":\"/bff/download/x\",\"expiresAt\":\"2026-10-05T00:00:42Z\"}");
        assertThat(anonymous.userIdAsLong()).isNull();
        assertThatThrownBy(() -> new AnalyticsScheduleStopped(null, "7", 3)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("AIA-07.04 EVT-AIA-03 ai.quota.exceeded: 사용자 한도만 userId, 조직 한도는 userId 없음")
    void quotaExceeded() {
        AiQuotaExceeded org = AiQuotaExceeded.organization(AiQuotaExceeded.LimitType.TOKENS, T);
        JsonNode tree = codec.toTree(DomainEvent.of(EventType.AI_QUOTA_EXCEEDED, 1, org, null, CLOCK));
        assertThat(tree.get("payload").propertyNames()).containsExactly("scope", "limitType", "resetAt");
        MessageSchemas.assertValid(MessageSchemas.DOMAIN_EVENT, tree);
        assertThat(EventType.AI_QUOTA_EXCEEDED.eventId()).isEqualTo("EVT-AIA-03");
        assertThat(AiQuotaExceeded.user(7, AiQuotaExceeded.LimitType.REQUESTS, T).userId()).isEqualTo(7L);
        assertThatThrownBy(() -> new AiQuotaExceeded(AiQuotaExceeded.Scope.USER, null, AiQuotaExceeded.LimitType.REQUESTS, T))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiQuotaExceeded(AiQuotaExceeded.Scope.ORG, 7L, AiQuotaExceeded.LimitType.REQUESTS, T))
                .isInstanceOf(IllegalArgumentException.class);
        // 스키마도 사용자 한도의 userId 누락을 잡는다
        var bad = (tools.jackson.databind.node.ObjectNode) tree.deepCopy();
        ((tools.jackson.databind.node.ObjectNode) bad.get("payload")).put("scope", "USER");
        assertThatThrownBy(() -> MessageSchemas.assertValid(MessageSchemas.DOMAIN_EVENT, bad)).hasMessageContaining("userId");
    }
}
