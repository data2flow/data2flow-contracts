package net.java21.data2flow.contracts.messaging;

import net.java21.data2flow.contracts.message.CanonicalTelemetry;
import net.java21.data2flow.contracts.message.ConfigChangedMessage;
import net.java21.data2flow.contracts.message.DomainEvent;
import net.java21.data2flow.contracts.message.EventType;
import net.java21.data2flow.contracts.message.Message;
import net.java21.data2flow.contracts.message.RawEnvelope;
import net.java21.data2flow.contracts.message.SourceTypes;
import net.java21.data2flow.contracts.message.event.SpaceChanged;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** OPS-02.02·ING-05.01: 메시지 공통 헤더(messageId·v·schema·organizationId·X-REQUEST-ID·occurredAt) */
class MessageHeadersTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-03T02:40:09Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("ING-05.01 TC-ING-063 텔레메트리 헤더는 본문과 같은 messageId·v·organizationId와 스키마 이름")
    void telemetryHeaders() {
        CanonicalTelemetry t = CanonicalTelemetry.builder().organizationId(7).sourceId(3).externalId("e").deviceId(17)
                .measuredAt(Instant.EPOCH).receivedAt(Instant.EPOCH).rawMessageId(1).build();
        Map<String, Object> h = MessageHeaders.of(t);
        assertThat(h).containsEntry(MessageHeaders.MESSAGE_ID, t.messageId().toString())
                .containsEntry(MessageHeaders.SCHEMA_VERSION, "1")
                .containsEntry(MessageHeaders.SCHEMA, "canonical-telemetry")
                .containsEntry(MessageHeaders.ORGANIZATION_ID, "7");
        assertThat(MessageHeaders.get(h, MessageHeaders.ORGANIZATION_ID)).isEqualTo("7");
        assertThat(MessageHeaders.get(h, "none")).isNull();
        assertThat(MessageHeaders.get(null, "none")).isNull();
    }

    @Test
    @DisplayName("OPS-02.02 도메인 이벤트 헤더는 라우팅 키·발생 시각·요청 ID를 싣는다")
    void eventHeaders() {
        DomainEvent<SpaceChanged> e = DomainEvent.of(EventType.SPACE_CHANGED, 1, new SpaceChanged(31, "MOVED", "/1/31"),
                "req-9", clock);
        assertThat(MessageHeaders.of(e)).containsEntry(MessageHeaders.SCHEMA, "space.changed")
                .containsEntry(MessageHeaders.OCCURRED_AT, "2026-10-03T02:40:09Z")
                .containsEntry(MessageHeaders.REQUEST_ID, "req-9")
                .containsEntry(MessageHeaders.ORGANIZATION_ID, "1");
        DomainEvent<SpaceChanged> noReq = DomainEvent.of(EventType.SPACE_CHANGED, 1, new SpaceChanged(31, "MOVED", "/1/31"),
                null, clock);
        assertThat(MessageHeaders.of(noReq)).doesNotContainKey(MessageHeaders.REQUEST_ID);
    }

    @Test
    @DisplayName("ING-01.01 원본·설정 변경 헤더, 스키마 표시가 없는 메시지는 기본 헤더만")
    void otherHeaders() {
        RawEnvelope raw = RawEnvelope.of(2, 3, SourceTypes.WEBHOOK, null, new byte[0], Instant.EPOCH, "i", "k");
        assertThat(MessageHeaders.of(raw)).containsEntry(MessageHeaders.ORGANIZATION_ID, "2")
                .containsEntry(MessageHeaders.SCHEMA, "raw-envelope");
        ConfigChangedMessage c = ConfigChangedMessage.upsert(ConfigChangedMessage.EntityType.SOURCE, 3, 1, 5, clock);
        assertThat(MessageHeaders.of(c)).containsEntry(MessageHeaders.ORGANIZATION_ID, "5");
        record Plain(int v, UUID messageId) implements Message {
        }
        assertThat(MessageHeaders.of(new Plain(1, UUID.randomUUID()))).containsOnlyKeys("messageId", "v");
    }
}
