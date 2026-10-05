package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * EVT-ANA-05 {@code analytics.schedule.stopped}: 일정 실행이 연속 3회 실패해 일정을 멈췄다(BR-ANA-11). 생산 data2flow-analytics 워커 →
 * 소비 core-api(일정 STOPPED_BY_FAILURE, 소유자 알림 센터).
 *
 * @param analysisId          분석 ID(문자열)
 * @param ownerUserId         분석 소유자 사용자 ID(문자열)
 * @param consecutiveFailures 연속 실패 횟수
 */
public record AnalyticsScheduleStopped(String analysisId, String ownerUserId, int consecutiveFailures) implements EventPayload {

    public AnalyticsScheduleStopped {
        if (analysisId == null || analysisId.isBlank() || ownerUserId == null) {
            throw new IllegalArgumentException("analysisId·ownerUserId는 필수입니다");
        }
    }

    /** 분석 ID를 숫자로. 숫자가 아니면 null */
    @JsonIgnore
    public Long analysisIdAsLong() {
        return AnalyticsIds.toLong(analysisId);
    }

    /** 소유자 ID를 숫자로. 숫자가 아니면 null */
    @JsonIgnore
    public Long ownerUserIdAsLong() {
        return AnalyticsIds.toLong(ownerUserId);
    }
}
