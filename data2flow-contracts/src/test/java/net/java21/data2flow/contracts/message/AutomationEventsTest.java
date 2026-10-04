package net.java21.data2flow.contracts.message;

import net.java21.data2flow.contracts.alarm.AlarmKeys;
import net.java21.data2flow.contracts.alarm.AlarmSeverity;
import net.java21.data2flow.contracts.alarm.AlarmSourceType;
import net.java21.data2flow.contracts.alarm.AlarmStatus;
import net.java21.data2flow.contracts.capability.ExpectedEffect;
import net.java21.data2flow.contracts.command.CommandPriority;
import net.java21.data2flow.contracts.message.event.AlarmSignal;
import net.java21.data2flow.contracts.message.event.AlarmStateChanged;
import net.java21.data2flow.contracts.message.event.CalendarSynced;
import net.java21.data2flow.contracts.message.event.CommandNoEffect;
import net.java21.data2flow.contracts.message.event.DriverCircuitChanged;
import net.java21.data2flow.contracts.message.event.EmergencyStopChanged;
import net.java21.data2flow.contracts.message.event.EventPayload;
import net.java21.data2flow.contracts.message.event.LoRaWanDownlinkAck;
import net.java21.data2flow.contracts.message.event.MaintenanceChanged;
import net.java21.data2flow.contracts.message.event.ReprocessJobFinished;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static net.java21.data2flow.contracts.message.M4M5EventSamples.T;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** M4·M5 도메인 이벤트(EVT-RUL·ACT·OPS·DSC·ING)의 모양과 판정 도우미 */
class AutomationEventsTest {

    private final MessageCodec codec = MessageCodec.create();
    private static final Clock CLOCK = Clock.fixed(T, ZoneOffset.UTC);

    @Test
    @DisplayName("RUL-01.01 TC-FLW-054 EVT-RUL-01 alarm.signal: RAISE는 심각도·제목 필수, CLEAR는 없어도 되고 키는 (flowId, nodeId, deviceId)로 고정")
    void alarmSignalShape() {
        String key = AlarmKeys.flow("f-7f3a", "n1", AlarmKeys.target(15L, 31L));
        assertThat(key).isEqualTo("flow:f-7f3a:n1:15");
        AlarmSignal clear = AlarmSignal.clear(key, AlarmSourceType.FLOW, null, "f-7f3a", 13, "n1", 15L, 31L, "temperature",
                25.9, T, "m-2");
        DomainEvent<AlarmSignal> event = DomainEvent.of(EventType.ALARM_SIGNAL, 1, clear, null, CLOCK);
        MessageSchemas.assertValid(event);
        JsonNode tree = codec.toTree(event);
        assertThat(tree.at("/payload/signal").asString()).isEqualTo("CLEAR");
        assertThat(tree.at("/payload/severity").isMissingNode()).isTrue();
        assertThatThrownBy(() -> new AlarmSignal(AlarmSignal.Signal.RAISE, key, AlarmSourceType.FLOW, null, "f", 1, "n", null,
                "t", null, null, null, null, null, T, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AlarmSignal(AlarmSignal.Signal.CLEAR, "x".repeat(201), AlarmSourceType.FLOW, null, "f", 1,
                "n", null, null, null, null, null, null, null, T, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AlarmSignal(null, key, AlarmSourceType.FLOW, null, "f", 1, "n", null, null, null, null,
                null, null, null, T, null)).isInstanceOf(IllegalArgumentException.class);
        // RAISE인데 severity가 빠진 JSON은 스키마에서도 걸린다
        var raise = (tools.jackson.databind.node.ObjectNode) codec.toTree(DomainEvent.of(EventType.ALARM_SIGNAL, 1,
                M4M5EventSamples.all().get(EventType.ALARM_SIGNAL), null, CLOCK));
        ((tools.jackson.databind.node.ObjectNode) raise.get("payload")).remove("severity");
        assertThat(MessageSchemas.validate(MessageSchemas.DOMAIN_EVENT, raise)).isNotEmpty();
    }

    @Test
    @DisplayName("RUL-02.02 TC-RUL-040 EVT-RUL-02 알람 상태 이벤트 종류를 라우팅 키 끝으로 찾고, alarm.signal은 상태 이벤트가 아니다")
    void alarmStateLookup() {
        assertThat(EventType.alarmState("acked")).isEqualTo(EventType.ALARM_ACKED);
        assertThat(EventType.alarmState("suppressed").eventId()).isEqualTo("EVT-RUL-02");
        assertThatThrownBy(() -> EventType.alarmState("signal")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EventType.alarmState("exploded")).isInstanceOf(IllegalArgumentException.class);
        AlarmStateChanged acked = (AlarmStateChanged) M4M5EventSamples.all().get(EventType.ALARM_ACKED);
        assertThat(acked.alarm().status().open()).isTrue();
        assertThat(acked.actor().id()).isEqualTo("5");
        assertThat(AlarmStatus.CLEARED.open()).isFalse();
        assertThatThrownBy(() -> new AlarmStateChanged(null, null, T)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> M4M5EventSamples.alarm(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(codec.mapper().readValue("\"PURPLE\"", AlarmSeverity.class)).isEqualTo(AlarmSeverity.UNKNOWN);
    }

    @Test
    @DisplayName("ACT-06.03 TC-ACT-110 BR-ACT-12 비상 정지는 AUTO·SCHEDULE·AI만 막고, 범위는 조직 전체·공간(하위 포함)이다")
    void emergencyStopScope() {
        assertThat(EmergencyStopChanged.Scope.blocks(CommandPriority.AUTO)).isTrue();
        assertThat(EmergencyStopChanged.Scope.blocks(CommandPriority.SCHEDULE)).isTrue();
        assertThat(EmergencyStopChanged.Scope.blocks(CommandPriority.AI)).isTrue();
        assertThat(EmergencyStopChanged.Scope.blocks(CommandPriority.MANUAL)).isFalse();
        assertThat(EmergencyStopChanged.Scope.blocks(CommandPriority.SAFETY)).isFalse();
        EmergencyStopChanged.Scope lab = EmergencyStopChanged.Scope.space(7);
        assertThat(lab.covers(List.of(1L, 7L, 31L))).isTrue();
        assertThat(lab.covers(List.of(1L, 8L))).isFalse();
        assertThat(lab.covers(List.of())).isFalse();
        assertThat(new EmergencyStopChanged.Scope(EmergencyStopChanged.Scope.Type.SPACE, 7L, false).covers(List.of(1L, 7L, 31L)))
                .isFalse();
        assertThat(new EmergencyStopChanged.Scope(EmergencyStopChanged.Scope.Type.SPACE, 7L, false).covers(List.of(1L, 7L)))
                .isTrue();
        assertThat(EmergencyStopChanged.Scope.organization().covers(List.of())).isTrue();
        assertThat(new EmergencyStopChanged.Scope(EmergencyStopChanged.Scope.Type.UNKNOWN, null, null).covers(List.of(1L)))
                .isFalse();
        assertThatThrownBy(() -> new EmergencyStopChanged.Scope(EmergencyStopChanged.Scope.Type.SPACE, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmergencyStopChanged(0, lab, null, null, T)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ACT-08.01 TC-ACT-132 EVT-ACT-04 효과 없음: 기대(기능의 expected_effects)와 관찰(+0.1℃) 변화량")
    void noEffectShape() {
        ExpectedEffect effect = new ExpectedEffect(new ExpectedEffect.When("set", null), "temperature",
                ExpectedEffect.Direction.DOWN, 15);
        CommandNoEffect.Expected expected = CommandNoEffect.Expected.of(effect);
        assertThat(expected.withinMinutes()).isEqualTo(15);
        assertThat(CommandNoEffect.Observed.between(27.0, 27.5).delta()).isEqualTo(0.5);
        assertThat(CommandNoEffect.Observed.between(null, 27.5).delta()).isNull();
        JsonNode tree = codec.toTree(DomainEvent.of(EventType.COMMAND_NO_EFFECT, 1,
                M4M5EventSamples.all().get(EventType.COMMAND_NO_EFFECT), null, CLOCK));
        assertThat(tree.at("/payload/expected/direction").asString()).isEqualTo("down");
        assertThatThrownBy(() -> new CommandNoEffect(null, 1, null, "x", expected, null, T))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DriverCircuitChanged(7, "MQTT", 1.5, T)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("OPS-05.01 TC-OPS-046 EVT-OPS-02 유지보수 대상 판정(공간은 하위 공간 포함, 기기는 그 기기만)")
    void maintenanceCovers() {
        MaintenanceChanged space = (MaintenanceChanged) M4M5EventSamples.all().get(EventType.OPS_MAINTENANCE_STARTED);
        assertThat(space.covers(null, 31L)).isTrue();
        assertThat(space.covers(15L, 33L)).isTrue();
        assertThat(space.covers(15L, 40L)).isFalse();
        assertThat(space.covers(15L, null)).isFalse();
        MaintenanceChanged device = (MaintenanceChanged) M4M5EventSamples.all().get(EventType.OPS_MAINTENANCE_ENDED);
        assertThat(device.covers(15L, 31L)).isTrue();
        assertThat(device.covers(16L, 31L)).isFalse();
        assertThat(device.covers(null, 31L)).isFalse();
        assertThatThrownBy(() -> new MaintenanceChanged(1, null, 1, null, false, false, T, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DSC-06.04 TC-DSC-151 BR-DSC-18 EVT-DSC-07 일정 날짜는 yyyy-MM-dd, 시각은 HH:mm:ss로 쓰고 종료일이 시작일보다 앞서면 거부")
    void calendarShape() {
        JsonNode tree = codec.toTree(DomainEvent.of(EventType.CALENDAR_SYNCED, 1,
                M4M5EventSamples.all().get(EventType.CALENDAR_SYNCED), null, CLOCK));
        assertThat(tree.at("/payload/events/0/startsOn").asString()).isEqualTo("2026-10-09");
        assertThat(tree.at("/payload/events/0/startTime").isMissingNode()).isTrue();
        assertThat(tree.at("/payload/events/1/startTime").asString()).startsWith("09:00");
        assertThat(tree.at("/payload/removedUids/0").asString()).isEqualTo("evt-0@school");
        assertThatThrownBy(() -> new CalendarSynced.Event("u", "t", "EXAM", LocalDate.parse("2026-10-10"),
                LocalDate.parse("2026-10-09"), null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new CalendarSynced(1, null, null).events()).isEmpty();
    }

    @Test
    @DisplayName("ING-01.04 TC-ING-025 EVT-ING-09 재처리 작업 끝: 상태·건수·구간, 모르는 상태는 UNKNOWN")
    void reprocessFinished() {
        ReprocessJobFinished done = (ReprocessJobFinished) M4M5EventSamples.all().get(EventType.INGEST_REPROCESS_FINISHED);
        assertThat(done.processed() + done.failed() + done.skipped()).isEqualTo(done.total());
        assertThat(EventType.INGEST_REPROCESS_FINISHED.routingKey()).isEqualTo("ingest.reprocess.finished");
        assertThat(codec.mapper().readValue("\"PAUSED\"", ReprocessJobFinished.Status.class))
                .isEqualTo(ReprocessJobFinished.Status.UNKNOWN);
        assertThatThrownBy(() -> new ReprocessJobFinished("x", null, null, null, T, T, 0, 0, 0, 0, null, null, T))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ACT-02.05 TC-ACT-052 ADR-043 ③ EVT-ACT-08 control.oscillation.blocked는 action이 내고 출처를 싣는다")
    void oscillationEvent() {
        EventPayload p = M4M5EventSamples.all().get(EventType.CONTROL_OSCILLATION_BLOCKED);
        JsonNode tree = codec.toTree(DomainEvent.of(EventType.CONTROL_OSCILLATION_BLOCKED, 1, p, null, CLOCK));
        assertThat(tree.get("type").asString()).isEqualTo("control.oscillation.blocked");
        assertThat(tree.at("/payload/source/type").asString()).isEqualTo("FLOW");
        assertThat(EventType.CONTROL_OSCILLATION_BLOCKED.eventId()).isEqualTo("EVT-ACT-08");
        assertThat(AlarmKeys.system("OSCILLATION", "15", "Switch")).isEqualTo("system:OSCILLATION:15:Switch");
    }

    @Test
    @DisplayName("ACT-03.03 ADR-054 ① EVT-ACT-09 lorawan.downlink.ack: 키 이름(fCntDown 등)·kind 없음은 ACK·TXACK는 확인 없음")
    void loRaWanDownlinkAck() {
        EventPayload p = M4M5EventSamples.all().get(EventType.LORAWAN_DOWNLINK_ACK);
        JsonNode tree = codec.toTree(DomainEvent.of(EventType.LORAWAN_DOWNLINK_ACK, 1, p, null, CLOCK));
        assertThat(tree.get("type").asString()).isEqualTo("lorawan.downlink.ack");
        assertThat(tree.get("payload").propertyNames()).containsExactly("sourceId", "devEui", "queueItemId", "acknowledged",
                "fCntDown", "at", "kind");
        assertThat(EventType.LORAWAN_DOWNLINK_ACK.eventId()).isEqualTo("EVT-ACT-09");

        LoRaWanDownlinkAck legacy = codec.mapper().readValue("""
                {"sourceId":1,"devEui":"24e124136d151606","queueItemId":"q-1","acknowledged":true,"at":"2026-10-04T03:12:04Z",
                 "future":1}""", LoRaWanDownlinkAck.class);
        assertThat(legacy.kind()).isNull();
        assertThat(legacy.effectiveKind()).isEqualTo(LoRaWanDownlinkAck.Kind.ACK);
        assertThat(legacy.fCntDown()).isNull();
        assertThat(codec.mapper().readValue("\"RXACK\"", LoRaWanDownlinkAck.Kind.class)).isEqualTo(LoRaWanDownlinkAck.Kind.UNKNOWN);
        assertThat(LoRaWanDownlinkAck.txAck(1, "24e124136d151606", "q-1", 7L, T).acknowledged()).isFalse();
        assertThatThrownBy(() -> new LoRaWanDownlinkAck(1, "24e124136d151606", "q-1", true, null, T, LoRaWanDownlinkAck.Kind.TXACK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LoRaWanDownlinkAck.ack(1, "24e124136d151606", " ", true, null, T))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
