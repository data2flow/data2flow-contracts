package net.java21.data2flow.contracts.alarm;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/** 억제 사유(alarms.suppressed_reason, BR-RUL-08): 유지보수 모드, 상위 원인(게이트웨이·소스 장애), 기기 오프라인 */
public enum SuppressedReason {
    MAINTENANCE, PARENT, DEVICE_OFFLINE,
    @JsonEnumDefaultValue
    UNKNOWN
}
