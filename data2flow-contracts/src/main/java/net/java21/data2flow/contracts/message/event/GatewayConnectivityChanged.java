package net.java21.data2flow.contracts.message.event;

import java.time.Instant;

/**
 * EVT-DEV-08 {@code gateway.connectivity.changed}: LoRaWAN 게이트웨이 연결 상태가 바뀌었다(DEV-05.03, 생산 core-api 게이트웨이 판정 →
 * 소비 core-api 알람·토폴로지 묶기 RUL-04.01, SSE). 시나리오 6: 게이트웨이 다운 → 알람 1건, 하위 기기 알람은 SUPPRESSED(PARENT).
 *
 * @param gatewayId  게이트웨이 ID
 * @param gatewayEui 게이트웨이 EUI(16진수 16자)
 * @param from       이전 상태
 * @param to         새 상태
 * @param lastSeenAt 마지막 수신 시각
 */
public record GatewayConnectivityChanged(long gatewayId, String gatewayEui, DeviceConnectivityChanged.Connectivity from,
                                         DeviceConnectivityChanged.Connectivity to, Instant lastSeenAt)
        implements EventPayload {
}
