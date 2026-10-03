package net.java21.data2flow.contracts.command;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import net.java21.data2flow.contracts.messaging.MessagingNames;

/**
 * 행동 요청 종류(ActionRequest {@code kind}, ACT-api §5.1)와 {@code data2flow.actions} 라우팅 키.
 *
 * <p>M3는 COMMAND만 쓴다. WORK_ORDER·ANALYSIS·REPORT·SPACE_MODE는 문서에 큐가 정해지지 않아 아직 두지 않는다(추가는 호환 변경).
 */
public enum ActionKind {
    /** 제어 명령 → {@code action.commands} */
    COMMAND(MessagingNames.ROUTING_KEY_COMMAND),
    /** 장면 실행 → {@code action.commands} */
    SCENE(MessagingNames.ROUTING_KEY_COMMAND),
    /** 알림 → {@code action.notifications} */
    NOTIFY(MessagingNames.ROUTING_KEY_NOTIFY),
    /** 외부 출력 → {@code action.sinks} */
    SINK(MessagingNames.ROUTING_KEY_SINK),
    /** 이 코드보다 새 생산자가 보낸 종류. 소비자는 DLQ로 보낸다 */
    @JsonEnumDefaultValue
    UNKNOWN(null);

    private final String routingKey;

    ActionKind(String routingKey) {
        this.routingKey = routingKey;
    }

    /** {@code data2flow.actions} direct 라우팅 키 */
    public String routingKey() {
        if (routingKey == null) {
            throw new IllegalStateException("UNKNOWN 종류는 발행할 수 없습니다");
        }
        return routingKey;
    }
}
