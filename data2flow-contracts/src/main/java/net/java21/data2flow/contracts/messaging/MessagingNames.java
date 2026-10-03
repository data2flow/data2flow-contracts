package net.java21.data2flow.contracts.messaging;

/** RabbitMQ 이름(design/architecture.md §4, ADR-020). vhost는 환경마다 다르고(data2flow·data2flow-stg·data2flow-dev) 이름은 같다. */
public final class MessagingNames {

    /** 원본 메시지 Super Stream(ingress → pipeline) */
    public static final String STREAM_RAW = "data2flow.raw";
    /** 정제된 텔레메트리 Super Stream(pipeline → flow-engine·core 실시간·analytics) */
    public static final String STREAM_TELEMETRY = "data2flow.telemetry";
    /** 도메인 이벤트 topic exchange */
    public static final String EXCHANGE_EVENTS = "data2flow.events";
    /** 행동(제어·알림·Sink) exchange */
    public static final String EXCHANGE_ACTIONS = "data2flow.actions";
    /** 런타임 설정 변경 fanout */
    public static final String EXCHANGE_CONFIG = "data2flow.config";

    /** 메시지 헤더·본문 공통 필드 */
    public static final String FIELD_MESSAGE_ID = "messageId";
    public static final String FIELD_SCHEMA_VERSION = "v";

    private MessagingNames() {
    }
}
