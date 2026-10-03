package net.java21.data2flow.contracts.message.event;

import java.time.Instant;

/**
 * EVT-DEV-03 {@code device.pending.created}: 처음 보는 기기가 수신 데이터로 발견되어 승인 대기(PENDING)로 등록되었다
 * (생산 core-api, ADR-031). EVT-ING-03은 대체되었다.
 *
 * @param deviceId    기기 ID
 * @param sourceId    데이터 소스 ID
 * @param externalId  기기 외부 ID(devEui 등)
 * @param name        자동으로 붙인 이름
 * @param firstSeenAt 처음 받은 시각
 */
public record DevicePendingCreated(long deviceId, long sourceId, String externalId, String name, Instant firstSeenAt)
        implements EventPayload {
}
