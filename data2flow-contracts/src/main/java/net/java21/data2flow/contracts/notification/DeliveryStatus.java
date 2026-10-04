package net.java21.data2flow.contracts.notification;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * 알림 발송 상태(notification_deliveries.status, RUL domain-model §3 = OPS §3.2).
 * {@code PENDING → SENT}, {@code PENDING → RETRYING → SENT | FAILED}, {@code PENDING → SKIPPED}(사유 {@link DeliverySkipReasons}),
 * {@code PENDING → DIGESTED}(묶음에 합쳐짐).
 */
public enum DeliveryStatus {
    PENDING, RETRYING, SENT, FAILED, SKIPPED, DIGESTED,
    @JsonEnumDefaultValue
    UNKNOWN;

    /** 더 바뀌지 않는 상태인가 */
    public boolean terminal() {
        return this == SENT || this == FAILED || this == SKIPPED || this == DIGESTED;
    }
}
