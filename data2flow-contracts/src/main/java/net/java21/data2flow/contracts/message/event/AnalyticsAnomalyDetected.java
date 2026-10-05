package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * EVT-ANA-02 {@code analytics.anomaly.detected}: 실시간 추론이 이상을 찾았다(ANA-06.01). 생산 data2flow-analytics 워커
 * ({@code RealtimeEngine._record}) → 소비 flow-engine(트리거 "이상 탐지 결과"), RUL 조건, core-api(화면).
 *
 * @param analysisId 분석 ID(문자열)
 * @param deviceId   기기 ID(문자열)
 * @param metricKey  측정 항목 키
 * @param occurredAt 측정 시각
 * @param value      측정값
 * @param score      이상 점수(|z|)
 * @param kind       SPIKE·DROP·LEVEL_SHIFT·VARIANCE
 * @param evidence   근거(기준선·기준 점수·기여 계열)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalyticsAnomalyDetected(String analysisId, String deviceId, String metricKey, Instant occurredAt, double value,
                                       double score, Kind kind, Evidence evidence) implements EventPayload {

    public enum Kind {
        SPIKE, DROP, LEVEL_SHIFT, VARIANCE,
        @JsonEnumDefaultValue
        UNKNOWN
    }

    /**
     * 근거. analytics는 세 키를 늘 싣는다(값이 없으면 null).
     *
     * @param baseline     그 시각의 기준선 값
     * @param threshold    이상으로 보는 점수 기준
     * @param contributors 기여 계열(실시간은 빈 목록)
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Evidence(Double baseline, Double threshold, List<Contributor> contributors) {

        public Evidence {
            contributors = contributors == null ? List.of() : List.copyOf(contributors);
        }
    }

    /** 기여 계열 */
    public record Contributor(String seriesKey, double weight) {
    }

    public AnalyticsAnomalyDetected {
        if (analysisId == null || deviceId == null || metricKey == null || occurredAt == null || kind == null || evidence == null) {
            throw new IllegalArgumentException("analysisId·deviceId·metricKey·occurredAt·kind·evidence는 필수입니다");
        }
    }

    /** 기기 ID를 숫자로. 숫자가 아니면 null */
    @JsonIgnore
    public Long deviceIdAsLong() {
        return AnalyticsIds.toLong(deviceId);
    }

    /** 분석 ID를 숫자로. 숫자가 아니면 null */
    @JsonIgnore
    public Long analysisIdAsLong() {
        return AnalyticsIds.toLong(analysisId);
    }
}
