package net.java21.data2flow.contracts.message.event;

import java.time.Instant;

/**
 * EVT-DSC-05 {@code source.no-data}(무수신 시작)·{@code source.data-resumed}(수신 재개). 생산 core-api(1분 주기 점검).
 *
 * @param sourceId       데이터 소스 ID
 * @param lastReceivedAt 마지막 수신 시각
 * @param thresholdSec   무수신 판정 기준(초, {@code no_data_alarm_after_sec})
 */
public record SourceDataActivity(long sourceId, Instant lastReceivedAt, int thresholdSec) implements EventPayload {
}
