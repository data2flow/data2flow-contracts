package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * EVT-DEV-06 {@code space.mode.changed}: 공간 운영 모드가 바뀌었다(DEV-11.02, BR-DEV-23, 생산 core-api 1분 계산 → 소비 flow-engine
 * 트리거, core-api 알람).
 *
 * @param spaceId 공간
 * @param from    이전 모드(OCCUPIED·UNOCCUPIED·HOLIDAY·MAINTENANCE). 처음이면 null
 * @param to      새 모드
 * @param source  판단 근거(SCHEDULE·CALENDAR·MAINTENANCE·OCCUPANCY·MANUAL)
 * @param at      바뀐 시각
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SpaceModeChanged(long spaceId, String from, String to, String source, Instant at) implements EventPayload {

    public static final String OCCUPIED = "OCCUPIED";
    public static final String UNOCCUPIED = "UNOCCUPIED";
    public static final String HOLIDAY = "HOLIDAY";
    public static final String MAINTENANCE = "MAINTENANCE";
}
