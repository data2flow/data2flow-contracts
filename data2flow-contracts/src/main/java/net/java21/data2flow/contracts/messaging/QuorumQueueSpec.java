package net.java21.data2flow.contracts.messaging;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Quorum Queue 정의(architecture.md §4.3, ADR-020): 명령·알림·Sink 큐와 서비스별 이벤트 큐. 수동 ACK, {@code delivery-limit=5},
 * 넘으면 DLX {@code data2flow.dlx}(라우팅 키 = 큐 이름) → {@code {queue}.dlq}.
 *
 * <pre>{@code
 * QuorumQueueSpec q = QuorumQueueSpec.ACTION_COMMANDS;
 * channel.exchangeDeclare(q.exchange(), "direct", true);
 * channel.exchangeDeclare(MessagingNames.EXCHANGE_DLX, "direct", true);
 * channel.queueDeclare(q.name(), true, false, false, q.arguments());
 * channel.queueBind(q.name(), q.exchange(), q.routingKey());
 * channel.queueDeclare(q.deadLetterQueue(), true, false, false, QuorumQueueSpec.deadLetterArguments());
 * channel.queueBind(q.deadLetterQueue(), MessagingNames.EXCHANGE_DLX, q.name());
 * }</pre>
 *
 * <p>3.13은 {@code delivery-limit} 기본값이 무제한이므로 큐 인자({@link #arguments()})와 운영 정책 둘 다에 넣는다(architecture.md §4.4).
 *
 * @param name          큐 이름
 * @param exchange      바인딩 exchange
 * @param routingKey    바인딩 키(이벤트 큐는 여러 개를 따로 묶으므로 null)
 * @param deliveryLimit 이 횟수만큼 실패(NACK·재전달)하면 DLQ로
 * @param prefetch      소비자 prefetch(architecture.md §4.3 표)
 */
public record QuorumQueueSpec(String name, String exchange, String routingKey, int deliveryLimit, int prefetch) {

    /** 제어 명령(ActionRequest kind=COMMAND·SCENE): data2flow-action/actuation, prefetch 20 */
    public static final QuorumQueueSpec ACTION_COMMANDS = new QuorumQueueSpec(MessagingNames.QUEUE_ACTION_COMMANDS,
            MessagingNames.EXCHANGE_ACTIONS, MessagingNames.ROUTING_KEY_COMMAND, MessagingNames.DELIVERY_LIMIT, 20);
    /** 알림(kind=NOTIFY): data2flow-action/notification */
    public static final QuorumQueueSpec ACTION_NOTIFICATIONS = new QuorumQueueSpec(MessagingNames.QUEUE_ACTION_NOTIFICATIONS,
            MessagingNames.EXCHANGE_ACTIONS, MessagingNames.ROUTING_KEY_NOTIFY, MessagingNames.DELIVERY_LIMIT, 20);
    /** Sink(kind=SINK): data2flow-action/sink */
    public static final QuorumQueueSpec ACTION_SINKS = new QuorumQueueSpec(MessagingNames.QUEUE_ACTION_SINKS,
            MessagingNames.EXCHANGE_ACTIONS, MessagingNames.ROUTING_KEY_SINK, MessagingNames.DELIVERY_LIMIT, 20);

    public QuorumQueueSpec {
        if (name == null || name.isBlank() || exchange == null || exchange.isBlank()) {
            throw new IllegalArgumentException("큐 이름과 exchange는 필수입니다");
        }
        if (deliveryLimit < 1 || prefetch < 1) {
            throw new IllegalArgumentException("deliveryLimit·prefetch는 1 이상이어야 합니다");
        }
    }

    /**
     * 서비스 이벤트 큐 {@code {service}.events}({@code data2flow.events} topic). 라우팅 키 바인딩은 서비스가 처리기 목록대로 건다
     * (architecture.md §4.5, 예: action ← {@code device.command.ack}, {@code device.state.reported}).
     */
    public static QuorumQueueSpec events(String service) {
        return new QuorumQueueSpec(MessagingNames.eventsQueue(service), MessagingNames.EXCHANGE_EVENTS, null,
                MessagingNames.DELIVERY_LIMIT, 20);
    }

    /** 실패 전용 큐 이름 {@code {name}.dlq} */
    public String deadLetterQueue() {
        return MessagingNames.deadLetterQueue(name);
    }

    /** 큐 선언 인자: quorum, delivery-limit, DLX(라우팅 키 = 큐 이름) */
    public Map<String, Object> arguments() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("x-queue-type", "quorum");
        args.put("x-delivery-limit", deliveryLimit);
        args.put("x-dead-letter-exchange", MessagingNames.EXCHANGE_DLX);
        args.put("x-dead-letter-routing-key", name);
        return args;
    }

    /** DLQ 선언 인자(quorum, DLX 없음: 사람이 확인하고 다시 보낸다) */
    public static Map<String, Object> deadLetterArguments() {
        return Map.of("x-queue-type", "quorum");
    }
}
