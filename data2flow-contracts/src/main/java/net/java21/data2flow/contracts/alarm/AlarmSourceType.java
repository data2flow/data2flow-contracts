package net.java21.data2flow.contracts.alarm;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/** 알람 출처(alarms.source_type): 규칙(규칙이 컴파일된 플로우 포함), 사용자 플로우의 알람 노드, 시스템 판정 */
public enum AlarmSourceType {
    RULE, FLOW, SYSTEM,
    @JsonEnumDefaultValue
    UNKNOWN
}
