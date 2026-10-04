package net.java21.data2flow.contracts.alarm;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/** 알람 해제 사유(alarms.clear_reason, API-RUL-12) */
public enum AlarmClearReason {
    /** 조건 해소(auto_clear) */
    AUTO,
    /** 사람이 해제 */
    MANUAL,
    /** 상위 알람 해제(BR-RUL-09) */
    PARENT_CLEARED,
    RULE_DELETED,
    /** 규칙 범위에서 기기가 빠짐(BR-RUL-06) */
    RULE_SCOPE_CHANGED,
    @JsonEnumDefaultValue
    UNKNOWN
}
