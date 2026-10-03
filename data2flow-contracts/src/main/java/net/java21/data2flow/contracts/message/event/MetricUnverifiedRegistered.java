package net.java21.data2flow.contracts.message.event;

import java.time.Instant;

/**
 * EVT-ING-04 {@code metric.unverified.registered}: 처음 보는 측정 키가 UNVERIFIED로 자동 등록되었다(ING-04.02).
 *
 * @param metricKey     측정 키
 * @param firstDeviceId 처음 보낸 기기 ID
 * @param firstSeenAt   처음 받은 시각
 */
public record MetricUnverifiedRegistered(String metricKey, long firstDeviceId, Instant firstSeenAt)
        implements EventPayload {
}
