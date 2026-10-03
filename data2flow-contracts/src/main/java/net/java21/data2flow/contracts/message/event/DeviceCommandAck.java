package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * EVT-ACT-06 = EVT-SIM-03 {@code device.command.ack}: 기기(드라이버 어댑터·가상 장비)가 명령을 받았다고 응답했다(BR-ACT-25).
 * 소비: data2flow-action(SENT → ACKED/FAILED). 같은 commandId 재전송에는 같은 응답을 다시 보낸다.
 *
 * @param commandId 명령 ID(action이 만든 UUID 문자열을 그대로 돌려준다)
 * @param deviceId  기기 ID
 * @param result    ACKED 또는 FAILED
 * @param reason    FAILED 이유(예: INVALID_COMMAND, DEVICE_NOT_SIMULATED). 없으면 null
 * @param at        응답 시각(가상 장비는 시뮬레이션 시각 정책을 따른다)
 * @param virtual   가상 장비 응답이면 true(SIM-07.01). 실제 드라이버는 false
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeviceCommandAck(String commandId, long deviceId, Result result, String reason, Instant at, boolean virtual)
        implements EventPayload {

    public enum Result {
        ACKED, FAILED,
        @JsonEnumDefaultValue
        UNKNOWN
    }

    public static DeviceCommandAck acked(String commandId, long deviceId, Instant at, boolean virtual) {
        return new DeviceCommandAck(commandId, deviceId, Result.ACKED, null, at, virtual);
    }

    public static DeviceCommandAck failed(String commandId, long deviceId, String reason, Instant at, boolean virtual) {
        return new DeviceCommandAck(commandId, deviceId, Result.FAILED, reason, at, virtual);
    }
}
