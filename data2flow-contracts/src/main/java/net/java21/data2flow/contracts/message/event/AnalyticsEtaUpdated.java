package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * EVT-ANA-03 {@code analytics.eta.updated}: 실시간 추론이 기준값 도달 예상 시각을 새로 냈다(ANA-02.09). 생산 data2flow-analytics 워커
 * ({@code RealtimeEngine._record}) → 소비 flow-engine(트리거 "기준 도달 예측"), core-api(화면·알림 센터).
 *
 * <p>analytics는 문서의 필드 뒤에 판정에 쓴 측정 시각 {@code occurredAt}과 측정값 {@code value}도 싣는다.
 *
 * @param analysisId  분석 ID(문자열)
 * @param deviceId    기기 ID(문자열)
 * @param metricKey   측정 항목 키
 * @param threshold   기준값
 * @param direction   UP(올라가며 닿음)·DOWN
 * @param etaAt       도달 예상 시각
 * @param minutesLeft 남은 시간(분)
 * @param confidence  HIGH·MEDIUM·LOW
 * @param occurredAt  판정에 쓴 측정 시각. 없을 수 있다
 * @param value       판정에 쓴 측정값. 없을 수 있다
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalyticsEtaUpdated(String analysisId, String deviceId, String metricKey, double threshold, Direction direction,
                                  Instant etaAt, double minutesLeft, Confidence confidence, Instant occurredAt, Double value)
        implements EventPayload {

    public enum Direction {
        UP, DOWN,
        @JsonEnumDefaultValue
        UNKNOWN
    }

    public enum Confidence {
        HIGH, MEDIUM, LOW,
        @JsonEnumDefaultValue
        UNKNOWN
    }

    public AnalyticsEtaUpdated {
        if (analysisId == null || deviceId == null || metricKey == null || direction == null || etaAt == null || confidence == null) {
            throw new IllegalArgumentException("analysisId·deviceId·metricKey·direction·etaAt·confidence는 필수입니다");
        }
    }

    /** 기기 ID를 숫자로. 숫자가 아니면 null */
    @JsonIgnore
    public Long deviceIdAsLong() {
        return AnalyticsIds.toLong(deviceId);
    }
}
