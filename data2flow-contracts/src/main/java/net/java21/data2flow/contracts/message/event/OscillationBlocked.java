package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.java21.data2flow.contracts.command.CommandSource;

import java.time.Instant;
import java.util.UUID;

/**
 * EVT-ACT-08 {@code control.oscillation.blocked}: 진동(같은 기기·기능에 반대 명령이 창 안에 반복) 때문에 명령을 BLOCKED(OSCILLATION)
 * 했다(BR-ACT-07: 60초 안에 3번째 반대 명령). 생산 action → 소비 core-api(WARNING 시스템 알람
 * {@code system:OSCILLATION:{deviceId}:{capability}}). ADR-043 열린 요청 ③.
 *
 * @param commandId  막힌 명령
 * @param deviceId   기기
 * @param spaceId    기기 공간. 없으면 null
 * @param capability 기능
 * @param command    명령
 * @param flips      창 안의 방향 전환 수
 * @param windowSec  판정 창(초)
 * @param source     막힌 명령의 출처(어느 플로우·규칙이 진동을 일으켰는지)
 * @param at         판정 시각
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OscillationBlocked(UUID commandId, long deviceId, Long spaceId, String capability, String command, int flips,
                                 int windowSec, CommandSource source, Instant at) implements EventPayload {

    public OscillationBlocked {
        if (commandId == null || capability == null || command == null || source == null || at == null) {
            throw new IllegalArgumentException("commandId·capability·command·source·at은 필수입니다");
        }
    }
}
