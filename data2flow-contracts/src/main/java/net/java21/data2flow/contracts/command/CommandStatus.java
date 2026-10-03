package net.java21.data2flow.contracts.command;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

import java.util.Locale;

/**
 * 명령 상태(ACT domain-model §3, ACT-02.02). EVT-ACT-01 라우팅 키는 {@code command.status.{소문자}}다(예: {@code command.status.applied}).
 */
public enum CommandStatus {
    REQUESTED(false),
    REJECTED(true),
    BLOCKED(true),
    SKIPPED(true),
    DELAYED(false),
    QUEUED(false),
    QUEUED_FOR_DOWNLINK(false),
    SENT(false),
    ACKED(false),
    APPLIED(true),
    TIMEOUT(true),
    FAILED(true),
    SUPERSEDED(true),
    CANCELLED(true),
    /** 이 코드보다 새 생산자가 보낸 상태 */
    @JsonEnumDefaultValue
    UNKNOWN(false);

    /** EVT-ACT-01 라우팅 키 접두사 */
    public static final String ROUTING_KEY_PREFIX = "command.status.";

    private final boolean terminal;

    CommandStatus(boolean terminal) {
        this.terminal = terminal;
    }

    /**
     * 끝 상태인지: APPLIED, FAILED, TIMEOUT, REJECTED, BLOCKED, SKIPPED, SUPERSEDED, CANCELLED. 상태 보고가 없는 드라이버는
     * ACKED를 끝으로 보지만 그 판단은 드라이버 설정에 달려 있어 여기서는 끝 상태가 아니다.
     */
    public boolean terminal() {
        return terminal;
    }

    /** 취소할 수 있는 상태(API-ACT-02: QUEUED·DELAYED·QUEUED_FOR_DOWNLINK) */
    public boolean cancellable() {
        return this == QUEUED || this == DELAYED || this == QUEUED_FOR_DOWNLINK;
    }

    /** 플로우 제어 노드 결과 포트: 성공(APPLIED, ACKED 끝) 쪽인지 */
    public boolean succeeded() {
        return this == APPLIED;
    }

    /** EVT-ACT-01 라우팅 키 */
    public String routingKey() {
        if (this == UNKNOWN) {
            throw new IllegalStateException("UNKNOWN 상태는 발행할 수 없습니다");
        }
        return ROUTING_KEY_PREFIX + name().toLowerCase(Locale.ROOT);
    }
}
