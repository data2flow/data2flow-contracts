package net.java21.data2flow.contracts.capability;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * 제어 효과 기대값(ACT-08.01, BR-ACT-20). 예: Thermostat {@code set mode=cool} → 15분 안에 temperature 하강.
 *
 * @param when          조건: 명령과 인자 일부
 * @param metric        확인할 측정 항목 키
 * @param direction     기대 방향
 * @param withinMinutes 확인 시간(분)
 */
public record ExpectedEffect(When when, String metric, Direction direction, int withinMinutes) {

    /** 효과를 기대하는 명령 조건 */
    public record When(String command, Map<String, Object> args) {
        public When {
            args = args == null ? Map.of() : Map.copyOf(args);
        }
    }

    public enum Direction {
        @JsonProperty("up") UP,
        @JsonProperty("down") DOWN
    }
}
