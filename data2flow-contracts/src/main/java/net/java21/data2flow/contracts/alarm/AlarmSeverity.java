package net.java21.data2flow.contracts.alarm;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * 알람 심각도(RUL domain-model {@code alarms.severity}, API-RUL-10). 위에서 아래로 심각하다. 알림 정책의 최소 심각도(BR-RUL-12)와
 * 사용자 수신 설정(OPS-06.05), 방해 금지의 CRITICAL 예외(BR-RUL-14)가 이 순서를 쓴다.
 */
public enum AlarmSeverity {
    CRITICAL, MAJOR, MINOR, WARNING, INFO,
    /** 이 코드보다 새 생산자가 보낸 값. 어떤 기준도 넘지 않는 것으로 본다 */
    @JsonEnumDefaultValue
    UNKNOWN;

    /** {@code minimum}과 같거나 더 심각한가. UNKNOWN은 언제나 false */
    public boolean atLeast(AlarmSeverity minimum) {
        if (this == UNKNOWN || minimum == null || minimum == UNKNOWN) {
            return false;
        }
        return ordinal() <= minimum.ordinal();
    }
}
