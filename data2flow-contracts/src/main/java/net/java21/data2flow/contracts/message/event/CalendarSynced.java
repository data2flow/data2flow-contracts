package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * EVT-DSC-07 {@code calendar.synced}: iCal·공휴일 수집 결과(DSC-06.03·06.04, 생산 ingress → 소비 core-api 조직 달력 반영 BR-DSC-18).
 * 일정은 UID로 중복을 막고, 원본에서 사라진 UID는 {@code removedUids}로 온다(수동 수정된 일정은 남기고 "원본 삭제됨" 표시).
 *
 * @param sourceId    외부 맥락 소스 ID
 * @param events      추가·바뀐 일정
 * @param removedUids 원본에서 사라진 UID
 */
public record CalendarSynced(long sourceId, List<Event> events, List<String> removedUids) implements EventPayload {

    public CalendarSynced {
        events = events == null ? List.of() : List.copyOf(events);
        removedUids = removedUids == null ? List.of() : List.copyOf(removedUids);
    }

    /**
     * 일정 하나. 하루 종일이면 시각이 없다. 날짜·시각은 조직 시간대의 현지 값이다.
     *
     * @param uid       iCal UID(공휴일은 {@code holiday:{yyyy-MM-dd}})
     * @param title     제목
     * @param type      일정 유형(매핑 결과, 예: HOLIDAY·VACATION·EXAM·EVENT)
     * @param startsOn  시작일
     * @param endsOn    종료일(포함)
     * @param startTime 시작 시각. 하루 종일이면 null
     * @param endTime   종료 시각. 하루 종일이면 null
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Event(String uid, String title, String type, LocalDate startsOn, LocalDate endsOn, LocalTime startTime,
                        LocalTime endTime) {

        public Event {
            if (uid == null || uid.isBlank() || startsOn == null || endsOn == null || endsOn.isBefore(startsOn)) {
                throw new IllegalArgumentException("일정 uid·startsOn·endsOn은 필수이고 endsOn ≥ startsOn입니다");
            }
        }
    }
}
