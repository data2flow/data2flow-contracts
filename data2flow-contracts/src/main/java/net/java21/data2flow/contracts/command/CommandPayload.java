package net.java21.data2flow.contracts.command;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 제어 명령 요청 본문(ActionRequest kind=COMMAND의 {@code payload}, ACT-api §5.1).
 *
 * @param target      대상
 * @param capability  기능(예: Thermostat)
 * @param command     명령(예: set)
 * @param args        인자(예: {@code {"mode":"cool","targetTemperature":24}})
 * @param awaitResult 결과를 기다리는 제어 노드면 true(EVT-ACT-01을 ok/failed 포트로 받음)
 */
public record CommandPayload(CommandTarget target, String capability, String command, Map<String, Object> args,
                             boolean awaitResult) {

    public CommandPayload {
        if (target == null) {
            throw new IllegalArgumentException("target은 필수입니다");
        }
        if (capability == null || capability.isBlank() || command == null || command.isBlank()) {
            throw new IllegalArgumentException("capability와 command는 필수입니다");
        }
        args = args == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(args));
    }
}
