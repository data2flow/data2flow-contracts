package net.java21.data2flow.contracts.test.message;

import net.java21.data2flow.contracts.command.CommandPriority;
import net.java21.data2flow.contracts.command.SourceType;
import net.java21.data2flow.contracts.message.ActionRequest;
import net.java21.data2flow.contracts.message.CanonicalTelemetry;
import net.java21.data2flow.contracts.message.DomainEvent;
import net.java21.data2flow.contracts.message.EventType;
import net.java21.data2flow.contracts.message.MessageCodec;
import net.java21.data2flow.contracts.message.MessageSchemas;
import net.java21.data2flow.contracts.message.RawEnvelope;
import net.java21.data2flow.contracts.message.event.DeviceCommandAck;
import net.java21.data2flow.contracts.message.event.EventPayload;
import net.java21.data2flow.contracts.messaging.DedupKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ING-02.01 TC-ING-033·ING-05.01 TC-ING-065: 공유 픽스처는 스키마를 통과하고 계약 record로 읽힌다 */
class MessageFixturesTest {

    private final MessageCodec codec = MessageCodec.create();
    static final List<String> TELEMETRY = MessageFixtures.CANONICAL_TELEMETRY;
    static final List<String> RAW = MessageFixtures.RAW_ENVELOPE;
    static final List<String> ACTIONS = MessageFixtures.ACTION_REQUEST;
    static final List<String> EVENTS = MessageFixtures.DOMAIN_EVENT;

    @ParameterizedTest
    @FieldSource("ACTIONS")
    @DisplayName("ACT-02.01 TC-ACT-027 행동 요청 공유 픽스처가 action-request.v1.json을 통과하고 COMMAND 본문으로 손실 없이 읽힌다")
    void actionRequestFixtures(String name) {
        MessageSchemas.assertValid(MessageSchemas.ACTION_REQUEST, codec.mapper().readTree(MessageFixtures.actionRequestJson(name)));
        ActionRequest request = MessageFixtures.actionRequest(name);
        MessageSchemas.assertValid(request);
        assertThat(codec.read(codec.write(request), ActionRequest.class)).isEqualTo(request);
        assertThat(request.commandPayload().capability()).isNotBlank();
        assertThat(request.priority()).isEqualTo(request.source().priority());
    }

    @Test
    @DisplayName("FLW-05.01 TC-ACT-027 플로우 제어 노드 픽스처: 공간 관계 대상, AUTO, 결과 대기")
    void flowCommandFixture() {
        ActionRequest request = MessageFixtures.actionRequest("flow-command-heatwave");
        assertThat(request.source().type()).isEqualTo(SourceType.FLOW);
        assertThat(request.priority()).isEqualTo(CommandPriority.AUTO);
        assertThat(request.routingKey()).isEqualTo("command");
        assertThat(request.commandPayload().target().isDevice()).isFalse();
        assertThat(request.commandPayload().args()).containsEntry("targetTemperature", 24);
        assertThat(request.commandPayload().awaitResult()).isTrue();
    }

    @Test
    @DisplayName("SIM-07.03 BR-ACT-23 기기 대상 플로우 명령 픽스처는 출처 공간(source.spaceId)을 싣는다")
    void flowDeviceCommandWithSourceSpace() {
        ActionRequest request = MessageFixtures.actionRequest("flow-command-device-source-space");
        assertThat(request.commandPayload().target().isDevice()).isTrue();
        assertThat(request.source().spaceId()).isEqualTo(31L);
        assertThat(MessageFixtures.actionRequest("flow-command-heatwave").source().spaceId()).isNull();
    }

    @ParameterizedTest
    @FieldSource("EVENTS")
    @DisplayName("SIM-03.04 TC-SIM-036 M3 도메인 이벤트 픽스처가 domain-event.v1.json을 통과하고 다시 써도 같은 바이트다")
    void domainEventFixtures(String name) {
        byte[] json = MessageFixtures.domainEventJson(name);
        MessageSchemas.assertValid(MessageSchemas.DOMAIN_EVENT, codec.mapper().readTree(json));
        DomainEvent<? extends EventPayload> event = MessageFixtures.domainEvent(name);
        assertThat(codec.mapper().readTree(codec.write(event))).isEqualTo(codec.mapper().readTree(json));
    }

    @Test
    @DisplayName("ACT-03.02 BR-ACT-25 가상 장비 ack 픽스처는 device.command.ack + virtual=true")
    void virtualAckFixture() {
        DomainEvent<? extends EventPayload> event = MessageFixtures.domainEvent("device-command-ack-virtual");
        assertThat(event.eventType()).isEqualTo(EventType.DEVICE_COMMAND_ACK);
        DeviceCommandAck ack = (DeviceCommandAck) event.payload();
        assertThat(ack.result()).isEqualTo(DeviceCommandAck.Result.ACKED);
        assertThat(ack.virtual()).isTrue();
    }

    @ParameterizedTest
    @FieldSource("TELEMETRY")
    @DisplayName("ING-02.01 TC-ING-033 표준 텔레메트리 픽스처가 canonical-telemetry.v1.json을 통과하고 다시 써도 스키마를 통과한다")
    void telemetryFixtures(String name) {
        MessageSchemas.assertValid(MessageSchemas.CANONICAL_TELEMETRY,
                codec.mapper().readTree(MessageFixtures.canonicalTelemetryJson(name)));
        CanonicalTelemetry t = MessageFixtures.canonicalTelemetry(name);
        MessageSchemas.assertValid(t);
        assertThat(codec.read(codec.write(t), CanonicalTelemetry.class)).isEqualTo(t);
    }

    @ParameterizedTest
    @FieldSource("RAW")
    @DisplayName("ING-01.01 TC-ING-001 원본 봉투 픽스처가 raw-envelope.v1.json을 통과한다")
    void rawFixtures(String name) {
        MessageSchemas.assertValid(MessageSchemas.RAW_ENVELOPE, codec.mapper().readTree(MessageFixtures.rawEnvelopeJson(name)));
        RawEnvelope raw = MessageFixtures.rawEnvelope(name);
        MessageSchemas.assertValid(raw);
    }

    @Test
    @DisplayName("DEV-03.02 아카데미 실측 6종 픽스처는 기기별 측정 항목을 담는다(research/01 §4)")
    void academyFixtures() {
        assertThat(MessageFixtures.ACADEMY_TELEMETRY).hasSize(6);
        assertThat(MessageFixtures.canonicalTelemetry("academy-am107").metrics()).extracting(CanonicalTelemetry.Metric::key)
                .containsExactly("co2", "tvoc", "pressure", "illumination", "infrared", "activity", "temperature", "humidity");
        assertThat(MessageFixtures.canonicalTelemetry("academy-ws302").metric("LAeq").unit()).isEqualTo("dB");
        assertThat(MessageFixtures.canonicalTelemetry("academy-am103").metric("battery").value()).isEqualTo(1.0);
        CanonicalTelemetry sim = MessageFixtures.canonicalTelemetry("virtual-pending-late");
        assertThat(sim.virtual()).isTrue();
        assertThat(sim.late()).isTrue();
        assertThat(sim.deviceStatus()).isEqualTo(CanonicalTelemetry.DeviceStatus.PENDING);
        assertThat(sim.metric("thi").derived()).isTrue();
    }

    @Test
    @DisplayName("ING-05.01 TC-ING-065 모르는 필드가 섞인 픽스처도 같은 값으로 읽히고 meta 확장 키는 보존된다")
    void unknownFieldsFixture() {
        CanonicalTelemetry withUnknown = MessageFixtures.canonicalTelemetry(MessageFixtures.TELEMETRY_WITH_UNKNOWN_FIELDS);
        CanonicalTelemetry plain = MessageFixtures.canonicalTelemetry("academy-ws302");
        assertThat(withUnknown.metrics()).isEqualTo(plain.metrics());
        assertThat(withUnknown.link()).isEqualTo(plain.link());
        assertThat(withUnknown.meta().extra()).containsKey("heartbeat");
        assertThat(MessageFixtures.allCanonicalTelemetryJson()).containsOnlyKeys(MessageFixtures.CANONICAL_TELEMETRY);
    }

    @Test
    @DisplayName("ING-04.04 ChirpStack 원본 픽스처의 중복 키는 deduplicationId에서 나온다")
    void chirpStackUplink() {
        RawEnvelope raw = MessageFixtures.rawEnvelope("chirpstack-ws302-uplink");
        assertThat(new String(MessageFixtures.chirpStackUplinkPayload(), StandardCharsets.UTF_8)).contains("\"devEui\":\"24e124743d012436\"");
        assertThat(DedupKeys.detect(raw.sourceId(), raw.topic(), raw.payload())).isEqualTo(raw.dedupKey());
        assertThat(MessageFixtures.rawEnvelope("simulation-virtual").virtual()).isTrue();
        assertThatThrownBy(() -> MessageFixtures.rawEnvelope("nope")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    @DisplayName("ING-02.01 TC-ING-033 WS302 원본 픽스처의 data 바이트를 Milesight 공식 순서(LAI·LAImax·LAeq)로 풀면 object 값과 같다")
    void ws302BytesMatchDecodedObject() {
        JsonNode uplink = codec.mapper().readTree(MessageFixtures.chirpStackUplinkPayload());
        Map<String, Double> decoded = decodeWs302(Base64.getDecoder().decode(uplink.get("data").asString()));
        JsonNode object = uplink.get("object");
        assertThat(decoded).containsOnlyKeys("battery", "LAI", "LAImax", "LAeq");
        decoded.forEach((key, value) -> assertThat(object.get(key).asDouble()).as(key).isEqualTo(value));
        assertThat(uplink.path("deviceInfo").path("deviceProfileName").asString()).isEqualTo("WS302");
    }

    /** Milesight WS302 공식 디코더의 바이트 순서: 01 75 배터리(1B), 05 5B 가중치(1B)+LAI·LAImax·LAeq(각 uint16 LE /10) */
    private static Map<String, Double> decodeWs302(byte[] bytes) {
        Map<String, Double> out = new LinkedHashMap<>();
        int i = 0;
        while (i + 1 < bytes.length) {
            int channel = bytes[i] & 0xff;
            int type = bytes[i + 1] & 0xff;
            i += 2;
            if (channel == 0x01 && type == 0x75) {
                out.put("battery", (double) (bytes[i] & 0xff));
                i += 1;
            } else if (channel == 0x05 && type == 0x5b) {
                out.put("LAI", u16(bytes, i + 1) / 10.0);
                out.put("LAImax", u16(bytes, i + 3) / 10.0);
                out.put("LAeq", u16(bytes, i + 5) / 10.0);
                i += 7;
            } else {
                throw new IllegalStateException("모르는 채널 " + channel + "/" + type);
            }
        }
        return out;
    }

    private static int u16(byte[] b, int i) {
        return (b[i] & 0xff) | (b[i + 1] & 0xff) << 8;
    }

    static final List<String> AUTOMATION_ACTIONS = MessageFixtures.AUTOMATION_ACTION_REQUEST;
    static final List<String> M4M5_EVENTS = java.util.stream.Stream.of(MessageFixtures.AUTOMATION_EVENT,
            MessageFixtures.DATA_MANAGEMENT_EVENT, MessageFixtures.DOWNLINK_EVENT).flatMap(List::stream).toList();
    static final List<String> DEBUG = MessageFixtures.FLOW_DEBUG;

    @ParameterizedTest
    @FieldSource("AUTOMATION_ACTIONS")
    @DisplayName("RUL-03.02 TC-RUL-081 · FLW-04.02 TC-FLW-058 알림·Sink 행동 요청 픽스처가 스키마를 통과하고 본문으로 손실 없이 읽힌다")
    void automationActionFixtures(String name) {
        MessageSchemas.assertValid(MessageSchemas.ACTION_REQUEST, codec.mapper().readTree(MessageFixtures.actionRequestJson(name)));
        ActionRequest request = MessageFixtures.actionRequest(name);
        assertThat(codec.read(codec.write(request), ActionRequest.class)).isEqualTo(request);
        switch (request.kind()) {
            case NOTIFY -> assertThat(request.notificationRequest().event()).isNotBlank();
            case SINK -> assertThat(request.sinkWriteRequest().records()).isNotEmpty();
            default -> throw new AssertionError("알림·Sink 픽스처가 아닙니다: " + request.kind());
        }
        assertThat(request.priority()).isEqualTo(request.source().priority());
    }

    @ParameterizedTest
    @FieldSource("M4M5_EVENTS")
    @DisplayName("RUL-02.01 · ACT-06.03 · DSC-06.04 M4·M5 도메인 이벤트 픽스처가 domain-event.v1.json을 통과하고 다시 써도 같은 값이다")
    void m4m5EventFixtures(String name) {
        byte[] json = MessageFixtures.domainEventJson(name);
        MessageSchemas.assertValid(MessageSchemas.DOMAIN_EVENT, codec.mapper().readTree(json));
        DomainEvent<? extends EventPayload> event = MessageFixtures.domainEvent(name);
        assertThat(codec.mapper().readTree(codec.write(event))).isEqualTo(codec.mapper().readTree(json));
    }

    @ParameterizedTest
    @FieldSource("DEBUG")
    @DisplayName("FLW-03.01 TC-FLW-066 라이브 뷰 디버그 픽스처가 flow-debug.v1.json을 통과하고 라우팅 키는 flow.{flowId}")
    void flowDebugFixtures(String name) {
        MessageSchemas.assertValid(MessageSchemas.FLOW_DEBUG, codec.mapper().readTree(MessageFixtures.flowDebugJson(name)));
        var m = MessageFixtures.flowDebug(name);
        assertThat(m.routingKey()).isEqualTo("flow." + m.flowId());
        assertThat(codec.read(codec.write(m), m.getClass())).isEqualTo(m);
    }

    @Test
    @DisplayName("FLW-03.04 TC-FLW-071·072 실행 추적 픽스처: 노드 순서, 행동 멱등 키, 처리 버전")
    void flowTraceFixture() {
        var trace = MessageFixtures.flowTrace(MessageFixtures.FLOW_TRACE.getFirst());
        assertThat(trace.steps()).extracting(s -> s.nodeId()).containsExactly("n-trg-1", "n-thr-1", "n-act-1");
        assertThat(trace.steps().getLast().action().idempotencyKey()).hasSize(64);
        assertThat(trace.version()).isEqualTo(13);
        assertThat(trace.hasDryRunActions()).isFalse();
    }
}
