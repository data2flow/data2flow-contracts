package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonInclude;
import net.java21.data2flow.contracts.capability.StateChange;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * EVT-ACT-02 {@code device.state.changed}: action이 reported 상태를 반영했다. 소비: flow-engine({@code trigger.deviceState}),
 * core-api(SSE·공간 보기), simulator(가상 장비 동기화 확인). 연결 상태 변화는 EVT-DEV-02가 따로 전한다.
 *
 * @param deviceId        기기 ID
 * @param spaceId         공간 ID. 없으면 null
 * @param connectivity    현재 연결 상태
 * @param reported        반영 뒤 reported 전체 {@code {capability:{attr:value}}}
 * @param changed         바뀐 속성
 * @param reportedVersion 반영한 보고 버전
 * @param delta           desired와 다른 속성
 * @param at              반영 시각
 * @param origin          변화 원인
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeviceStateChanged(long deviceId, Long spaceId, Connectivity connectivity,
                                 Map<String, Map<String, Object>> reported, List<StateChange> changed,
                                 long reportedVersion, Map<String, Map<String, Object>> delta, Instant at, Origin origin)
        implements EventPayload {

    public enum Connectivity {
        UNKNOWN, ONLINE, OFFLINE
    }

    public enum Origin {
        /** 명령 적용 결과 */
        COMMAND,
        /** 기기에서 직접 바뀜(리모컨 등, "기기에서 직접 변경됨") */
        DEVICE_LOCAL,
        /** 재연결 뒤 재적용 */
        RECONNECT,
        @JsonEnumDefaultValue
        UNKNOWN
    }
}
