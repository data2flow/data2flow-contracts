package net.java21.data2flow.contracts.test.message;

import net.java21.data2flow.contracts.message.ActionRequest;
import net.java21.data2flow.contracts.message.CanonicalTelemetry;
import net.java21.data2flow.contracts.message.DomainEvent;
import net.java21.data2flow.contracts.message.MessageCodec;
import net.java21.data2flow.contracts.message.RawEnvelope;
import net.java21.data2flow.contracts.message.event.EventPayload;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 생산자와 소비자가 함께 쓰는 공유 메시지 픽스처(TC-ING-033 "공유 픽스처", TC-ING-065 "소비자가 같은 공유 픽스처를 역직렬화").
 *
 * <p>pipeline은 디코더 출력이 이 픽스처와 같은 모양인지, flow-engine·core-api(SSE)·analytics 소비자는 이 픽스처를 읽을 수 있는지
 * 계약 테스트에서 확인한다. 아카데미 실측 기기 6종(DEV-03.02: EM300-TH, EM320-TH, EM500-CO2, AM103, AM107, WS302)의 값을 담았다.
 * M3(가상 폐루프)의 행동 요청(TC-ACT-027: flow-engine 생산자와 action 소비자가 같은 픽스처)과 ACT·SIM 도메인 이벤트도 있다.
 * 파일은 {@code classpath:data2flow/contracts/fixtures/{canonical-telemetry|raw-envelope|action-request|domain-event}/{이름}.json}에 있다.
 */
public final class MessageFixtures {

    /** 아카데미 실측 기기 6종 표준 텔레메트리 */
    public static final List<String> ACADEMY_TELEMETRY = List.of(
            "academy-em300-th", "academy-em320-th", "academy-em500-co2", "academy-am103", "academy-am107", "academy-ws302");

    /** 표준 텔레메트리 전체: 아카데미 6종 + 가상·승인 대기·늦은 도착·파생·품질 코드 + 모르는 필드가 섞인 것 */
    public static final List<String> CANONICAL_TELEMETRY = List.of(
            "academy-em300-th", "academy-em320-th", "academy-em500-co2", "academy-am103", "academy-am107", "academy-ws302",
            "virtual-pending-late", "with-unknown-fields");

    /** 모르는 필드(최상위·측정값·link·meta)가 섞인 표준 텔레메트리. 소비자는 무시하고 읽어야 한다 */
    public static final String TELEMETRY_WITH_UNKNOWN_FIELDS = "with-unknown-fields";

    /** 원본 봉투: ChirpStack v4 업링크(WS302), Webhook 단일 값, 시뮬레이터 가상 메시지 */
    public static final List<String> RAW_ENVELOPE = List.of(
            "chirpstack-ws302-uplink", "webhook-single-value", "simulation-virtual");

    /** 행동 요청: 플로우 "고온이면 냉방" 제어 노드(공간 관계 대상), 사용자 기기 명령(모르는 필드 포함) */
    public static final List<String> ACTION_REQUEST = List.of("flow-command-heatwave", "user-command-device");

    /**
     * M3 도메인 이벤트: 가상 장비 ack·상태 보고(EVT-SIM-03 = EVT-ACT-06·07, 시뮬레이터가 실제로 내는 바이트 모양), 명령 상태(EVT-ACT-01),
     * 실행 상태(EVT-SIM-01), 장애 라벨(EVT-SIM-02)
     */
    public static final List<String> DOMAIN_EVENT = List.of("device-command-ack-virtual", "device-state-reported-virtual",
            "command-status-applied", "sim-run-started", "sim-fault-started");

    private static final MessageCodec CODEC = MessageCodec.create();

    private MessageFixtures() {
    }

    public static byte[] canonicalTelemetryJson(String name) {
        return load("canonical-telemetry/" + name + ".json");
    }

    public static CanonicalTelemetry canonicalTelemetry(String name) {
        return CODEC.read(canonicalTelemetryJson(name), CanonicalTelemetry.class);
    }

    /** 이름 → JSON 본문(순서 유지) */
    public static Map<String, byte[]> allCanonicalTelemetryJson() {
        Map<String, byte[]> all = new LinkedHashMap<>();
        CANONICAL_TELEMETRY.forEach(name -> all.put(name, canonicalTelemetryJson(name)));
        return all;
    }

    public static byte[] rawEnvelopeJson(String name) {
        return load("raw-envelope/" + name + ".json");
    }

    public static RawEnvelope rawEnvelope(String name) {
        return CODEC.read(rawEnvelopeJson(name), RawEnvelope.class);
    }

    /** ChirpStack v4 {@code application/.../event/up} 원본 JSON(research/01 §6 예시에 deduplicationId·nsTime을 채운 것) */
    public static byte[] chirpStackUplinkPayload() {
        return rawEnvelope("chirpstack-ws302-uplink").payload();
    }

    public static byte[] actionRequestJson(String name) {
        return load("action-request/" + name + ".json");
    }

    public static ActionRequest actionRequest(String name) {
        return CODEC.read(actionRequestJson(name), ActionRequest.class);
    }

    public static byte[] domainEventJson(String name) {
        return load("domain-event/" + name + ".json");
    }

    public static DomainEvent<? extends EventPayload> domainEvent(String name) {
        return CODEC.readEvent(domainEventJson(name));
    }

    private static byte[] load(String path) {
        String resource = "/data2flow/contracts/fixtures/" + path;
        try (InputStream in = MessageFixtures.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("픽스처가 없습니다: " + resource);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
