package net.java21.data2flow.contracts.alarm;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/** 알람 상태(RUL domain-model §3 alarm.status). CLEARED는 다시 열지 않는다(같은 키가 다시 발생하면 새 알람) */
public enum AlarmStatus {
    ACTIVE, ACKNOWLEDGED, SUPPRESSED, CLEARED,
    @JsonEnumDefaultValue
    UNKNOWN;

    /** 열린 알람인가(BR-RUL-02: 같은 alarm_key에 열린 알람은 하나) */
    public boolean open() {
        return this == ACTIVE || this == ACKNOWLEDGED || this == SUPPRESSED;
    }
}
