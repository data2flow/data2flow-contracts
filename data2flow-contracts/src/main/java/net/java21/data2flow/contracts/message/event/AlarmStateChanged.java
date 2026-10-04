package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonInclude;
import net.java21.data2flow.contracts.alarm.AlarmSnapshot;

import java.time.Instant;

/**
 * EVT-RUL-02 {@code alarm.raised}·{@code alarm.reraised}·{@code alarm.acked}·{@code alarm.cleared}·{@code alarm.flapping}·
 * {@code alarm.suppressed}: 알람 상태가 바뀌었다(생산 core-api → 소비 flow-engine {@code trigger.alarm}, core-api SSE, ai).
 * 알림 요청은 이 이벤트가 아니라 EVT-RUL-03(행동 요청 NOTIFY)으로 따로 간다. 플래핑 해제도 {@code alarm.flapping}이고
 * {@code alarm.flapping=false}다.
 *
 * @param alarm 바뀐 뒤의 알람
 * @param actor 바꾼 주체. 시스템 판정이면 null 또는 SYSTEM
 * @param at    바뀐 시각
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AlarmStateChanged(AlarmSnapshot alarm, Actor actor, Instant at) implements EventPayload {

    public AlarmStateChanged {
        if (alarm == null || at == null) {
            throw new IllegalArgumentException("alarm·at은 필수입니다");
        }
    }

    /** 바꾼 주체(alarm_events.actor_type·actor_id) */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Actor(Type type, String id) {

        public static Actor user(long userId) {
            return new Actor(Type.USER, Long.toString(userId));
        }

        public enum Type {
            USER, SYSTEM, FLOW, MESSENGER,
            @JsonEnumDefaultValue
            UNKNOWN
        }
    }
}
