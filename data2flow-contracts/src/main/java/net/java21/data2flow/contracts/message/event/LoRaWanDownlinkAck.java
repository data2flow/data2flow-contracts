package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * EVT-ACT-09 {@code lorawan.downlink.ack}: ChirpStack v4가 LoRaWAN 다운링크 큐 항목의 결과를 알렸다(ACT-03.03, ADR-054 남은 것 ①).
 * 생산 ingress(ChirpStack MQTT 통합 {@code application/{appId}/device/{devEui}/event/ack}·{@code …/event/txack} 구독만, QoS 1) →
 * 소비 action(다운링크를 등록할 때 저장한 {@code queueItemId}로 명령을 찾아 SENT → ACKED/FAILED).
 *
 * <ul>
 *   <li>{@link Kind#ACK}(ChirpStack {@code event/ack}): 확인형(confirmed) 다운링크에 기기가 답했는지. {@code acknowledged=false}면 기기가
 *       확인하지 않았다.</li>
 *   <li>{@link Kind#TXACK}(ChirpStack {@code event/txack}): 게이트웨이가 다운링크를 무선으로 내보냈다. 기기 확인 정보가 없어
 *       {@code acknowledged}는 항상 false다. 비확인형(unconfirmed) 다운링크는 이것이 마지막 신호다.</li>
 * </ul>
 *
 * <p>이중 ingress(DUAL_ACTIVE)는 같은 ack를 두 번 낼 수 있다. 소비자는 상태 전이로 멱등 처리한다.
 *
 * @param sourceId     ack를 받은 데이터 소스 ID
 * @param devEui       기기 DevEUI(16자리 16진수, 소문자). 기기 외부 ID와 같다
 * @param queueItemId  ChirpStack 큐 항목 ID(다운링크 등록 응답의 {@code id})
 * @param acknowledged 기기가 확인했으면 true. TXACK는 항상 false
 * @param fCntDown     다운링크 프레임 카운터. 없으면 null
 * @param at           ChirpStack 이벤트 시각({@code time}). 없으면 ingress 수신 시각
 * @param kind         ACK 또는 TXACK. null이면 ACK(이 필드가 없던 생산자와 호환)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoRaWanDownlinkAck(long sourceId, String devEui, String queueItemId, boolean acknowledged, Long fCntDown,
                                 Instant at, Kind kind) implements EventPayload {

    public enum Kind {
        ACK, TXACK,
        @JsonEnumDefaultValue
        UNKNOWN
    }

    public LoRaWanDownlinkAck {
        if (sourceId < 1 || devEui == null || devEui.isBlank() || queueItemId == null || queueItemId.isBlank() || at == null) {
            throw new IllegalArgumentException("sourceId·devEui·queueItemId·at은 필수입니다");
        }
        if (kind == Kind.TXACK && acknowledged) {
            throw new IllegalArgumentException("TXACK는 기기 확인 정보가 없어 acknowledged가 false여야 합니다");
        }
    }

    /** ChirpStack {@code event/ack} */
    public static LoRaWanDownlinkAck ack(long sourceId, String devEui, String queueItemId, boolean acknowledged, Long fCntDown,
                                         Instant at) {
        return new LoRaWanDownlinkAck(sourceId, devEui, queueItemId, acknowledged, fCntDown, at, Kind.ACK);
    }

    /** ChirpStack {@code event/txack} */
    public static LoRaWanDownlinkAck txAck(long sourceId, String devEui, String queueItemId, Long fCntDown, Instant at) {
        return new LoRaWanDownlinkAck(sourceId, devEui, queueItemId, false, fCntDown, at, Kind.TXACK);
    }

    /** 실제 종류(없으면 ACK) */
    @JsonIgnore
    public Kind effectiveKind() {
        return kind == null ? Kind.ACK : kind;
    }
}
