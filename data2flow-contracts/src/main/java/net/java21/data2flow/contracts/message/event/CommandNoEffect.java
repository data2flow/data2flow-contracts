package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.java21.data2flow.contracts.capability.ExpectedEffect;

import java.time.Instant;
import java.util.UUID;

/**
 * EVT-ACT-04 {@code command.no-effect}: 명령이 APPLIED됐는데 기대 효과가 정해진 시간 안에 나타나지 않았다(ACT-08.01, BR-ACT-20,
 * 같은 기기 1시간에 1회). 생산 action → 소비 flow-engine(트리거), core-api(기기 이력, 선택 시 알람·작업 지시).
 *
 * @param commandId  명령 ID
 * @param deviceId   기기
 * @param spaceId    기기 공간. 없으면 null
 * @param capability 기능
 * @param expected   기대 효과
 * @param observed   관찰값
 * @param at         판정 시각
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommandNoEffect(UUID commandId, long deviceId, Long spaceId, String capability, Expected expected,
                              Observed observed, Instant at) implements EventPayload {

    public CommandNoEffect {
        if (commandId == null || capability == null || expected == null || at == null) {
            throw new IllegalArgumentException("commandId·capability·expected·at은 필수입니다");
        }
    }

    /** 기대: 측정 항목·방향·확인 시간(분) */
    public record Expected(String metric, ExpectedEffect.Direction direction, int withinMinutes) {

        public static Expected of(ExpectedEffect effect) {
            return new Expected(effect.metric(), effect.direction(), effect.withinMinutes());
        }
    }

    /** 관찰: 시작값·끝값·변화량(끝 - 시작). 측정이 없으면 null */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Observed(Double start, Double end, Double delta) {

        public static Observed between(Double start, Double end) {
            return new Observed(start, end, start == null || end == null ? null : end - start);
        }
    }
}
