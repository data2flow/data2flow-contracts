package net.java21.data2flow.contracts.messaging;

import net.java21.data2flow.contracts.identity.DataflowHeaders;
import net.java21.data2flow.contracts.message.CanonicalTelemetry;
import net.java21.data2flow.contracts.message.ConfigChangedMessage;
import net.java21.data2flow.contracts.message.DomainEvent;
import net.java21.data2flow.contracts.message.Message;
import net.java21.data2flow.contracts.message.MessageSchema;
import net.java21.data2flow.contracts.message.RawEnvelope;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 메시지 공통 헤더(AMQP 메시지 속성·스트림 애플리케이션 속성, ING-api §3 "공통 헤더").
 *
 * <p>본문을 열지 않고도 로그·추적·라우팅을 할 수 있게 본문과 같은 값을 헤더에도 싣는다. 값은 모두 문자열이다.
 * 추적 헤더({@value #TRACEPARENT})는 {@link MessageTracing}이 넣는다.
 */
public final class MessageHeaders {

    public static final String MESSAGE_ID = MessagingNames.FIELD_MESSAGE_ID;
    public static final String SCHEMA_VERSION = MessagingNames.FIELD_SCHEMA_VERSION;
    /** 스키마 이름(예: {@code canonical-telemetry}, 도메인 이벤트는 라우팅 키) */
    public static final String SCHEMA = "schema";
    public static final String ORGANIZATION_ID = "organizationId";
    public static final String REQUEST_ID = DataflowHeaders.REQUEST_ID;
    public static final String OCCURRED_AT = "occurredAt";
    /** W3C Trace Context(OPS-02.03) */
    public static final String TRACEPARENT = "traceparent";
    public static final String TRACESTATE = "tracestate";

    private MessageHeaders() {
    }

    /** 메시지에서 공통 헤더를 만든다. 수정할 수 있는 맵이므로 추적 헤더를 이어서 넣을 수 있다 */
    public static Map<String, Object> of(Message message) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put(MESSAGE_ID, message.messageId().toString());
        headers.put(SCHEMA_VERSION, Integer.toString(message.v()));
        MessageSchema schema = message.getClass().getAnnotation(MessageSchema.class);
        switch (message) {
            case RawEnvelope raw -> headers.put(ORGANIZATION_ID, Long.toString(raw.organizationId()));
            case CanonicalTelemetry t -> headers.put(ORGANIZATION_ID, Long.toString(t.organizationId()));
            case ConfigChangedMessage c -> headers.put(ORGANIZATION_ID, c.orgId());
            case DomainEvent<?> e -> {
                headers.put(SCHEMA, e.type());
                headers.put(ORGANIZATION_ID, Long.toString(e.organizationId()));
                headers.put(OCCURRED_AT, e.occurredAt().toString());
                if (e.requestId() != null) {
                    headers.put(REQUEST_ID, e.requestId());
                }
            }
            default -> {
            }
        }
        if (schema != null) {
            headers.putIfAbsent(SCHEMA, schema.name());
        }
        return headers;
    }

    /** 헤더 맵에서 문자열 값을 꺼낸다(AMQP가 주는 LongString 등도 toString). 없으면 null */
    public static String get(Map<String, ?> headers, String name) {
        Object value = headers == null ? null : headers.get(name);
        return value == null ? null : value.toString();
    }
}
