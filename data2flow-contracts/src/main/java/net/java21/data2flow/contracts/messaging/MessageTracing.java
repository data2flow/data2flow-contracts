package net.java21.data2flow.contracts.messaging;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

import java.util.Map;

/**
 * 메시지 헤더로 분산 추적을 잇는다(OPS-02.03, deployment.md "RabbitMQ 메시지 헤더까지 전파"). 업링크 1건이
 * ingress → {@code data2flow.raw} → pipeline → {@code data2flow.telemetry} → flow-engine으로 이어지는 하나의 추적이 된다.
 *
 * <p>W3C {@value MessageHeaders#TRACEPARENT} 헤더를 스트림 애플리케이션 속성·AMQP 헤더에 싣는다. Micrometer Tracing을 쓰므로
 * OTel 브리지가 있으면 OTel로, 추적을 끄면({@link #noop()}) 아무것도 하지 않는다. 추적을 꺼도 메시지 처리는 같다.
 *
 * <pre>{@code
 * // 생산(ingress): 발행 스팬을 열고 헤더에 넣은 뒤 confirm을 받으면 닫는다
 * Map<String, Object> headers = MessageHeaders.of(envelope);
 * Span span = tracing.startProducerSpan(MessagingNames.STREAM_RAW, headers);
 * producer.send(..., headers).whenComplete((ok, e) -> tracing.end(span, e));
 *
 * // 소비(pipeline): 헤더의 추적을 부모로 처리 스팬
 * Span span = tracing.startConsumerSpan(MessagingNames.STREAM_RAW, applicationProperties);
 * try (Tracer.SpanInScope scope = tracing.inScope(span)) { handle(message); } finally { span.end(); }
 * }</pre>
 */
public final class MessageTracing {

    /** OTel 메시징 의미 규약 태그 */
    public static final String TAG_DESTINATION = "messaging.destination.name";
    public static final String TAG_SYSTEM = "messaging.system";
    public static final String SYSTEM_RABBITMQ = "rabbitmq";

    private final Tracer tracer;
    private final Propagator propagator;

    public MessageTracing(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    /** 추적을 끈 서비스용. 헤더를 바꾸지 않고 스팬도 기록하지 않는다 */
    public static MessageTracing noop() {
        return new MessageTracing(Tracer.NOOP, Propagator.NOOP);
    }

    /** 현재 스팬의 추적 문맥을 헤더에 넣는다. 현재 스팬이 없으면 그대로 둔다 */
    public Map<String, Object> inject(Map<String, Object> headers) {
        Span current = tracer.currentSpan();
        if (current != null) {
            propagator.inject(current.context(), headers, Map::put);
        }
        return headers;
    }

    /** 발행 스팬(PRODUCER)을 시작하고 그 문맥을 헤더에 넣는다. 현재 스팬이 있으면 그 자식이다 */
    public Span startProducerSpan(String destination, Map<String, Object> headers) {
        Span span = tracer.spanBuilder()
                .name("publish " + destination)
                .kind(Span.Kind.PRODUCER)
                .tag(TAG_SYSTEM, SYSTEM_RABBITMQ)
                .tag(TAG_DESTINATION, destination)
                .start();
        propagator.inject(span.context(), headers, Map::put);
        return span;
    }

    /** 처리 스팬(CONSUMER)을 시작한다. 헤더에 추적 문맥이 있으면 그 자식이고, 없으면 새 추적이다 */
    public Span startConsumerSpan(String destination, Map<String, ?> headers) {
        return propagator.extract(headers, MessageHeaders::get)
                .name("process " + destination)
                .kind(Span.Kind.CONSUMER)
                .tag(TAG_SYSTEM, SYSTEM_RABBITMQ)
                .tag(TAG_DESTINATION, destination)
                .start();
    }

    /** 스팬을 현재 스팬으로 둔다(로그 MDC의 traceId도 이 값이 된다) */
    public Tracer.SpanInScope inScope(Span span) {
        return tracer.withSpan(span);
    }

    /** 스팬을 닫는다. 오류가 있으면 기록한다 */
    public void end(Span span, Throwable error) {
        if (error != null) {
            span.error(error);
        }
        span.end();
    }
}
