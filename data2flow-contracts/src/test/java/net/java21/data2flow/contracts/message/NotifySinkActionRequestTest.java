package net.java21.data2flow.contracts.message;

import net.java21.data2flow.contracts.alarm.AlarmSeverity;
import net.java21.data2flow.contracts.command.ActionIdempotencyKeys;
import net.java21.data2flow.contracts.command.ActionKind;
import net.java21.data2flow.contracts.command.CommandPayload;
import net.java21.data2flow.contracts.command.CommandPriority;
import net.java21.data2flow.contracts.command.CommandSource;
import net.java21.data2flow.contracts.command.CommandTarget;
import net.java21.data2flow.contracts.notification.NotificationEvents;
import net.java21.data2flow.contracts.notification.NotificationRecipient;
import net.java21.data2flow.contracts.notification.NotificationRequest;
import net.java21.data2flow.contracts.sink.SinkMode;
import net.java21.data2flow.contracts.sink.SinkWriteRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RUL-03·FLW-04: 행동 요청 NOTIFY(EVT-RUL-03 → action.notifications)·SINK(→ action.sinks) 본문 계약 */
class NotifySinkActionRequestTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-03T01:12:03Z"), ZoneOffset.UTC);
    private final MessageCodec codec = MessageCodec.create();

    private static NotificationRequest alarmNotify() {
        return NotificationRequest.forAlarm(9001, NotificationEvents.ALARM_RAISED, 1, AlarmSeverity.MAJOR,
                List.of(NotificationRecipient.onCall("TELEGRAM"), NotificationRecipient.user(5, NotificationRecipient.WEB)),
                Map.of("TELEGRAM", "alarm.raised.default"), Map.of("title", "실습실 고온", "value", 29.4), 60,
                "https://data2flow.java21.net/alarms/9001");
    }

    @Test
    @DisplayName("RUL-03.02 TC-RUL-081 알림 요청: 스키마 통과, 같은 값으로 읽힘, 라우팅 키 notify, 멱등 키는 알람·사건·순번으로 고정")
    void notifyRequestRoundTrip() {
        String key = ActionIdempotencyKeys.notifyRequest(9001, NotificationEvents.ALARM_RAISED, 1, null);
        ActionRequest req = ActionRequest.notify(1, key, CommandSource.system(), null, alarmNotify(), CLOCK);
        MessageSchemas.assertValid(req);
        ActionRequest read = codec.read(codec.write(req), ActionRequest.class);
        assertThat(read).isEqualTo(req);
        assertThat(read.routingKey()).isEqualTo("notify");
        assertThat(read.priority()).isEqualTo(CommandPriority.SAFETY);
        NotificationRequest n = read.notificationRequest();
        assertThat(n).isEqualTo(alarmNotify());
        assertThat(n.templateKeyFor("TELEGRAM")).isEqualTo("alarm.raised.default");
        assertThat(n.templateKeyFor("WEB")).isNull();
        assertThat(n.needsPolicyResolution()).isFalse();
        assertThat(key).hasSize(64).isEqualTo(ActionIdempotencyKeys.notifyRequest(9001, "alarm.raised", 1, null));
        assertThat(ActionIdempotencyKeys.notifyRequest(9001, "alarm.raised", 1, 2)).isNotEqualTo(key);
        assertThatThrownBy(read::commandPayload).isInstanceOf(MessageFormatException.class);
        assertThatThrownBy(read::sinkWriteRequest).isInstanceOf(MessageFormatException.class);
    }

    @Test
    @DisplayName("RUL-03.03 TC-RUL-074 BR-RUL-16 에스컬레이션 단계(1~3)와 플로우 알림 노드(정책 ID만, 수신자 계산은 action)")
    void escalationAndFlowNotify() {
        NotificationRequest step2 = alarmNotify().withEscalation(2);
        assertThat(step2.escalation().stepNo()).isEqualTo(2);
        assertThatThrownBy(() -> alarmNotify().withEscalation(4)).isInstanceOf(IllegalArgumentException.class);
        NotificationRequest flow = new NotificationRequest(null, NotificationEvents.FLOW_NOTIFY, null, null, 3L, null,
                Map.of(NotificationRequest.DEFAULT_TEMPLATE, "flow.co2.high"), Map.of("co2", 1500), 120, "space-31", null,
                null, true);
        assertThat(flow.needsPolicyResolution()).isTrue();
        assertThat(flow.templateKeyFor("TELEGRAM")).isEqualTo("flow.co2.high");
        ActionRequest req = ActionRequest.notify(1, ActionIdempotencyKeys.flow("f-1", "n-notify", "m-1"),
                CommandSource.flow("f-1", 2, "n-notify", "m-1"), null, flow, CLOCK);
        MessageSchemas.assertValid(req);
        assertThat(req.priority()).isEqualTo(CommandPriority.AUTO);
        assertThat(NotificationEvents.defaultTemplateKey("alarm.raised")).isEqualTo("alarm.raised.default");
        assertThatThrownBy(() -> NotificationEvents.defaultTemplateKey(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("RUL-03.06 BR-RUL-15 알림 요청 검증: 사건 필수, 수신자나 정책 중 하나, 묶기 창 0 또는 60~600")
    void notifyValidation() {
        assertThatThrownBy(() -> new NotificationRequest(1L, " ", null, null, null, List.of(NotificationRecipient.onCall("T")),
                null, null, null, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationRequest(1L, "e", null, null, null, null, null, null, null, null, null, null,
                null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationRequest(1L, "e", null, null, 2L, null, null, null, 30, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationRequest(0L, "e", null, null, 2L, null, null, null, 0, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new NotificationRequest(null, "e", null, null, 2L, null, null, null, 0, null, null, null, null)
                .aggregateWindowSec()).isZero();
        assertThatThrownBy(() -> new NotificationRecipient(NotificationRecipient.Type.USER, null, "TELEGRAM", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationRecipient(null, null, "TELEGRAM", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(NotificationRecipient.user(5, "TELEGRAM").recipientKey()).isEqualTo("USER:5");
        assertThat(NotificationRecipient.channelDefault("TELEGRAM").recipientKey()).isEqualTo("CHANNEL_DEFAULT");
        assertThat(new NotificationRecipient(NotificationRecipient.Type.ON_CALL, null, "TELEGRAM", "-100123").recipientKey())
                .isEqualTo("ON_CALL@-100123");
        // 스키마도 같은 규칙을 본다
        ActionRequest ok = ActionRequest.notify(1, "k-1", CommandSource.system(), null, alarmNotify(), CLOCK);
        ObjectNode tree = (ObjectNode) codec.toTree(ok);
        ((ObjectNode) tree.get("payload")).put("aggregateWindowSec", 30);
        assertThat(MessageSchemas.validate(MessageSchemas.ACTION_REQUEST, tree)).isNotEmpty();
        ((ObjectNode) tree.get("payload")).put("aggregateWindowSec", 60).remove("recipients");
        assertThat(MessageSchemas.validate(MessageSchemas.ACTION_REQUEST, tree)).isNotEmpty();
    }

    @Test
    @DisplayName("RUL-03.05 BR-RUL-17 발송 멱등 키 sha256(alarmId, eventType, recipient, channel, eventSeq)는 수신자·채널마다 다르다")
    void deliveryKeys() {
        String a = ActionIdempotencyKeys.notificationDelivery(9001, "alarm.raised", "USER:5", "TELEGRAM", 1);
        assertThat(a).hasSize(64).isEqualTo(ActionIdempotencyKeys.notificationDelivery(9001, "alarm.raised", "USER:5",
                "TELEGRAM", 1));
        assertThat(ActionIdempotencyKeys.notificationDelivery(9001, "alarm.raised", "USER:6", "TELEGRAM", 1)).isNotEqualTo(a);
        assertThat(ActionIdempotencyKeys.notificationDelivery("req-key", "flow.notify", "USER:5", "TELEGRAM")).hasSize(64);
        assertThatThrownBy(() -> ActionIdempotencyKeys.notificationDelivery(1, "e", null, "T", 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("FLW-04.02 TC-FLW-058 Sink 요청: 레코드 100건 = 요청 1개, UPSERT 키 필수, 라우팅 키 sink, 스키마 통과")
    void sinkRequest() {
        List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            records.add(Map.of("device_id", 15, "ts", "2026-10-03T01:" + (10 + i % 50) + ":00Z", "temperature", 27));
        }
        List<SinkWriteRequest> batches = SinkWriteRequest.batches(4, "room_temp", SinkMode.UPSERT, List.of("device_id", "ts"),
                records, SinkWriteRequest.DEFAULT_BATCH_SIZE);
        assertThat(batches).hasSize(3).extracting(SinkWriteRequest::batchIndex).containsExactly(0, 1, 2);
        assertThat(batches.get(2).records()).hasSize(50);
        assertThat(SinkWriteRequest.batches(4, "t", SinkMode.INSERT, null, records.subList(0, 100), 100))
                .singleElement().extracting(SinkWriteRequest::batchIndex).isNull();
        assertThat(SinkWriteRequest.batches(4, "t", SinkMode.INSERT, null, List.of(), 100)).isEmpty();

        SinkWriteRequest first = batches.getFirst();
        ActionRequest req = ActionRequest.sink(1, ActionIdempotencyKeys.flow("f-1", "n-sink", "m-1", 0),
                CommandSource.flow("f-1", 2, "n-sink", "m-1"), null, first, CLOCK);
        MessageSchemas.assertValid(req);
        ActionRequest read = codec.read(codec.write(req), ActionRequest.class);
        assertThat(read.kind()).isEqualTo(ActionKind.SINK);
        assertThat(read.routingKey()).isEqualTo("sink");
        assertThat(read.sinkWriteRequest().records()).hasSize(100);
        assertThat(read.sinkWriteRequest().upsertKeys()).containsExactly("device_id", "ts");
        assertThatThrownBy(read::notificationRequest).isInstanceOf(MessageFormatException.class);
    }

    @Test
    @DisplayName("FLW-04.03 BR-FLW-28 Sink 요청 검증: 1~1000건, 빈 레코드 거부, UPSERT 레코드에 키 열 필수, 깨진 본문은 DLQ 대상")
    void sinkValidation() {
        Map<String, Object> rec = Map.of("device_id", 15);
        assertThatThrownBy(() -> new SinkWriteRequest(0, "t", SinkMode.INSERT, null, List.of(rec), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SinkWriteRequest(1, " ", SinkMode.INSERT, null, List.of(rec), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SinkWriteRequest(1, "t", SinkMode.UPSERT, List.of(), List.of(rec), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SinkWriteRequest(1, "t", SinkMode.UPSERT, List.of("ts"), List.of(rec), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SinkWriteRequest(1, "t", SinkMode.INSERT, null, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SinkWriteRequest(1, "t", SinkMode.INSERT, null, List.of(Map.of()), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SinkWriteRequest(1, "t", SinkMode.INSERT, null, List.of(rec), -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SinkWriteRequest.batches(1, "t", SinkMode.INSERT, null, List.of(rec), 1001))
                .isInstanceOf(IllegalArgumentException.class);
        List<Map<String, Object>> tooMany = new ArrayList<>();
        for (int i = 0; i <= SinkWriteRequest.MAX_RECORDS; i++) {
            tooMany.add(rec);
        }
        assertThatThrownBy(() -> new SinkWriteRequest(1, "t", SinkMode.INSERT, null, tooMany, null))
                .isInstanceOf(IllegalArgumentException.class);
        ActionRequest broken = ActionRequest.of(ActionKind.SINK, 1, "k", CommandSource.system(), CommandPriority.SAFETY, null,
                codec.mapper().createObjectNode().put("connectionId", 1), CLOCK);
        assertThatThrownBy(broken::sinkWriteRequest).isInstanceOf(MessageFormatException.class);
        ActionRequest command = ActionRequest.command(1, "k2", CommandSource.user(5), null,
                new CommandPayload(CommandTarget.device(15), "Switch", "set", Map.of("on", true), false), CLOCK);
        assertThatThrownBy(command::notificationRequest).isInstanceOf(MessageFormatException.class);
    }
}
