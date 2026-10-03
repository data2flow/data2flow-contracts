package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * EVT-DEV-02 {@code device.connectivity.changed}: 기기 오프라인 판정이 바뀌었다(생산 pipeline, 예상 주기 × 배수 동안 수신 없음).
 *
 * @param deviceId            기기 ID
 * @param from                이전 상태. 처음 판정이면 null
 * @param to                  새 상태
 * @param lastSeenAt          마지막 수신 시각
 * @param expectedIntervalSec 예상 보고 주기(초)
 * @param multiplier          오프라인 판정 배수
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeviceConnectivityChanged(long deviceId, Connectivity from, Connectivity to, Instant lastSeenAt,
                                        int expectedIntervalSec, double multiplier) implements EventPayload {

    public enum Connectivity {
        ONLINE, OFFLINE
    }
}
