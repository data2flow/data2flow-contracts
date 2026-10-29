package net.java21.data2flow.contracts.message.event;

import java.time.LocalDate;

/**
 * EVT-CMP-05 {@code compliance.streak.broken}: 공간의 연속 달성이 끊겼다(CMP-02.01, BR-CMP-11, 생산 core-api → 소비 data2flow-action 운영자
 * 알림, SSE). 일 충족률이 프로필 기준 미만이거나 결측률이 품질 기준을 넘은 날 생긴다.
 *
 * @param spaceId      공간 ID
 * @param profileId    프로필 ID
 * @param brokenOn     끊긴 날(조직 시간대 기준 일)
 * @param brokenMetric 원인 항목(이탈 분이 가장 큰 항목, 결측으로 끊겼으면 결측률이 가장 큰 항목)
 * @param previousDays 끊기기 전까지 이어진 일수
 */
public record ComplianceStreakBroken(long spaceId, long profileId, LocalDate brokenOn, String brokenMetric, int previousDays)
        implements EventPayload {

    public ComplianceStreakBroken {
        if (brokenOn == null || previousDays < 0) {
            throw new IllegalArgumentException("brokenOn은 필수이고 previousDays는 0 이상입니다");
        }
    }
}
