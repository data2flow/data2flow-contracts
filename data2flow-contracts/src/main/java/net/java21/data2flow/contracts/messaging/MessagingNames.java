package net.java21.data2flow.contracts.messaging;

/**
 * RabbitMQ 이름(design/architecture.md §4, ADR-020·030). vhost는 환경마다 다르고(data2flow·data2flow-stg·data2flow-dev) 그 안의 이름은 같다.
 * 스트림 정의는 {@link SuperStreamSpec}, Quorum 큐 정의는 {@link QuorumQueueSpec}, 소비자 그룹은 {@link ConsumerGroups}, 라우팅 키는 {@link StreamRoutingKeys}와
 * {@link net.java21.data2flow.contracts.message.EventType}, 메시지 헤더는 {@link MessageHeaders}.
 */
public final class MessagingNames {

    /** 운영 vhost */
    public static final String VHOST_PROD = "data2flow";
    /** staging vhost(시뮬레이터 데이터만, ADR-030) */
    public static final String VHOST_STAGING = "data2flow-stg";
    /** 로컬 개발 vhost. 개발자끼리 함께 쓰므로 소비자 그룹에 개발자 이름을 붙인다 */
    public static final String VHOST_DEV = "data2flow-dev";

    /** 원본 메시지 Super Stream(ingress → pipeline) */
    public static final String STREAM_RAW = "data2flow.raw";
    /** 정제된 텔레메트리 Super Stream(pipeline → flow-engine·core 실시간·analytics) */
    public static final String STREAM_TELEMETRY = "data2flow.telemetry";
    /** 도메인 이벤트 topic exchange */
    public static final String EXCHANGE_EVENTS = "data2flow.events";
    /** 행동(제어·알림·Sink) direct exchange */
    public static final String EXCHANGE_ACTIONS = "data2flow.actions";
    /** 런타임 설정 변경 fanout(본문 {@link net.java21.data2flow.contracts.message.ConfigChangedMessage}) */
    public static final String EXCHANGE_CONFIG = "data2flow.config";
    /** 라이브 디버그 topic exchange(손실 허용) */
    public static final String EXCHANGE_DEBUG = "data2flow.debug";
    /** Dead Letter direct exchange. 라우팅 키는 원래 큐 이름, 대상 큐는 {@code {queue}.dlq} */
    public static final String EXCHANGE_DLX = "data2flow.dlx";

    /** 서비스별 이벤트 소비 큐(Quorum) 접미사: {@code {service}.events} */
    public static final String EVENTS_QUEUE_SUFFIX = ".events";
    /** DLQ 접미사 */
    public static final String DLQ_SUFFIX = ".dlq";
    /** 제어 명령 큐({@code data2flow.actions} · {@code command}) */
    public static final String QUEUE_ACTION_COMMANDS = "action.commands";
    /** 알림 큐({@code data2flow.actions} · {@code notify}) */
    public static final String QUEUE_ACTION_NOTIFICATIONS = "action.notifications";
    /** Sink 큐({@code data2flow.actions} · {@code sink}) */
    public static final String QUEUE_ACTION_SINKS = "action.sinks";
    /** Quorum Queue 실패 한도(3.13은 기본 무제한이라 정책으로 반드시 명시, architecture.md §4.4) */
    public static final int DELIVERY_LIMIT = 5;
    /** {@code data2flow.actions} 라우팅 키: 제어 명령·장면 → {@value #QUEUE_ACTION_COMMANDS} */
    public static final String ROUTING_KEY_COMMAND = "command";
    /** {@code data2flow.actions} 라우팅 키: 알림 → {@value #QUEUE_ACTION_NOTIFICATIONS} */
    public static final String ROUTING_KEY_NOTIFY = "notify";
    /** {@code data2flow.actions} 라우팅 키: Sink → {@value #QUEUE_ACTION_SINKS} */
    public static final String ROUTING_KEY_SINK = "sink";

    /** 라이브 뷰 디버그 임시 큐 최대 길이(EVT-FLW-01: 넘으면 오래된 것부터 버림) */
    public static final int DEBUG_QUEUE_MAX_LENGTH = 1000;

    /** 메시지 본문 공통 필드 */
    public static final String FIELD_MESSAGE_ID = "messageId";
    public static final String FIELD_SCHEMA_VERSION = "v";

    private MessagingNames() {
    }

    /** 서비스 이벤트 큐 이름. 예: {@code eventsQueue("core")} → {@code core.events} */
    public static String eventsQueue(String service) {
        return service + EVENTS_QUEUE_SUFFIX;
    }

    /** 라이브 뷰 디버그 라우팅 키 {@code flow.{flowId}}({@code data2flow.debug} topic, EVT-FLW-01) */
    public static String debugRoutingKey(String flowId) {
        if (flowId == null || flowId.isBlank()) {
            throw new IllegalArgumentException("flowId가 비어 있습니다");
        }
        return "flow." + flowId;
    }

    /**
     * 라이브 뷰 디버그 임시 큐 선언 인자(core-api가 열린 편집기의 flowId만 바인딩, 손실 허용): classic, {@code x-max-length} 1000,
     * {@code x-overflow=drop-head}. 큐는 exclusive·auto-delete로 선언한다.
     */
    public static java.util.Map<String, Object> debugQueueArguments() {
        java.util.Map<String, Object> args = new java.util.LinkedHashMap<>();
        args.put("x-queue-type", "classic");
        args.put("x-max-length", DEBUG_QUEUE_MAX_LENGTH);
        args.put("x-overflow", "drop-head");
        return args;
    }

    /** DLQ 이름. 예: {@code action.commands.dlq} */
    public static String deadLetterQueue(String queue) {
        return queue + DLQ_SUFFIX;
    }
}
