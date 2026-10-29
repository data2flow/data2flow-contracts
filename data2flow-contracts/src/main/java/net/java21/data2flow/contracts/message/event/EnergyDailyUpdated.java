package net.java21.data2flow.contracts.message.event;

import java.time.LocalDate;
import java.util.List;

/**
 * EVT-ENE-01 {@code energy.daily.updated}: 에너지 일 집계({@code data2flow_analytics.energy_daily})를 썼다(ENE-01.01·01.02, 생산 data2flow-analytics
 * → 소비 core-api 대시보드 캐시·월간 리포트, DSH).
 *
 * @param days       쓴 날짜(사이트 시간대 기준), 하나 이상
 * @param spaceIds   행을 쓴 공간 ID(문자열)
 * @param recomputed 다시 계산 요청(API-ENE-80)이나 늦은 데이터 재계산으로 쓴 것이면 true, 매일 02:00 확정이면 false
 */
public record EnergyDailyUpdated(List<LocalDate> days, List<String> spaceIds, boolean recomputed) implements EventPayload {

    public EnergyDailyUpdated {
        if (days == null || days.isEmpty()) {
            throw new IllegalArgumentException("days는 하나 이상입니다");
        }
        days = List.copyOf(days);
        spaceIds = spaceIds == null ? List.of() : List.copyOf(spaceIds);
    }
}
