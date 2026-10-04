package net.java21.data2flow.contracts.message.event;

import java.time.Instant;

/**
 * EVT-TSD-04 {@code retention.purged}: 보관 기간이 지난 데이터 정리(TSD-02.01·05.01·05.02, 생산 pipeline 야간 → 소비 core-api 운영 기록,
 * analytics).
 *
 * @param dataClass 데이터 종류(raw·telemetry·telemetry_1h …)
 * @param rangeFrom 정리 구간 시작
 * @param rangeTo   정리 구간 끝
 * @param rows      정리한 행
 * @param archived  콜드 보관(Parquet)으로 옮긴 뒤 지웠는가
 */
public record RetentionPurged(String dataClass, Instant rangeFrom, Instant rangeTo, long rows, boolean archived)
        implements EventPayload {
}
