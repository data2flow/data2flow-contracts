package net.java21.data2flow.contracts.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 명령 대상(ActionRequest {@code payload.target}): 기기 하나 또는 공간 관계로 찾는 기기들.
 * 예: {@code {"deviceId":15}} 또는 {@code {"spaceId":31,"relation":"controls","capability":"Thermostat","includeChildren":false}}.
 * 관계 대상은 action이 실행 시점에 기기별 명령으로 펼친다(TC-ACT-039).
 *
 * @param deviceId        기기 ID(기기 대상)
 * @param spaceId         공간 ID(관계 대상)
 * @param relation        공간-기기 관계(예: {@code controls})
 * @param capability      관계 대상에서 고를 기능
 * @param includeChildren 하위 공간 포함
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommandTarget(Long deviceId, Long spaceId, String relation, String capability, Boolean includeChildren) {

    /** 관계 대상의 기본 관계 */
    public static final String RELATION_CONTROLS = "controls";

    public CommandTarget {
        if ((deviceId == null) == (spaceId == null)) {
            throw new IllegalArgumentException("target은 deviceId 또는 spaceId 중 하나만 가집니다");
        }
        if (deviceId != null && deviceId < 1) {
            throw new IllegalArgumentException("deviceId는 1 이상이어야 합니다");
        }
        if (spaceId != null && (relation == null || relation.isBlank())) {
            throw new IllegalArgumentException("공간 대상에는 relation이 필요합니다");
        }
    }

    public static CommandTarget device(long deviceId) {
        return new CommandTarget(deviceId, null, null, null, null);
    }

    public static CommandTarget space(long spaceId, String relation, String capability, boolean includeChildren) {
        return new CommandTarget(null, spaceId, relation, capability, includeChildren);
    }

    @JsonIgnore
    public boolean isDevice() {
        return deviceId != null;
    }
}
