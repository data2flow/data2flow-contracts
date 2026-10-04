package net.java21.data2flow.contracts.message.event;

import java.time.Instant;

/**
 * EVT-ACT-05 {@code driver.circuit.opened}·{@code driver.circuit.closed}: 드라이버 서킷 브레이커가 열렸다·닫혔다(BR-ACT-14: 1분
 * 실패율 50% 초과 → 30초 동안 즉시 FAILED(DRIVER_UNAVAILABLE)). 생산 action → 소비 core-api(운영 알람 → RUL 시스템 알람
 * {@code system:DRIVER_CIRCUIT_OPEN:{driverId}}).
 *
 * @param driverId    드라이버 ID
 * @param type        드라이버 종류(VIRTUAL·MQTT·LORAWAN·LG_THINQ …)
 * @param failureRate 판정 때 실패율(0~1)
 * @param at          판정 시각
 */
public record DriverCircuitChanged(long driverId, String type, double failureRate, Instant at) implements EventPayload {

    public DriverCircuitChanged {
        if (driverId < 1 || type == null || at == null || failureRate < 0 || failureRate > 1) {
            throw new IllegalArgumentException("driverId·type·at은 필수이고 failureRate는 0~1입니다");
        }
    }
}
