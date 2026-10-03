package net.java21.data2flow.contracts.messaging;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** OPS-02.03: 메시지 헤더(traceparent)로 서비스 사이 추적을 잇는다(AT-OPS-04.2의 메시지 구간) */
class MessageTracingTest {

    private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
    private final SdkTracerProvider provider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
    private final OpenTelemetrySdk otel = OpenTelemetrySdk.builder().setTracerProvider(provider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance())).build();
    private final io.opentelemetry.api.trace.Tracer otelTracer = otel.getTracer("test");
    private final OtelCurrentTraceContext context = new OtelCurrentTraceContext();
    private final Tracer tracer = new OtelTracer(otelTracer, context, event -> { },
            new OtelBaggageManager(context, List.of(), List.of()));
    private final MessageTracing tracing = new MessageTracing(tracer, new OtelPropagator(otel.getPropagators(), otelTracer));

    @AfterEach
    void close() {
        provider.close();
    }

    @Test
    @DisplayName("OPS-02.03 TC-OPS-021 발행 스팬의 traceparent를 받은 쪽 처리 스팬이 부모로 이어받아 같은 traceId가 된다")
    void producerAndConsumerShareTrace() {
        Map<String, Object> headers = new HashMap<>();
        Span producer = tracing.startProducerSpan(MessagingNames.STREAM_RAW, headers);
        tracing.end(producer, null);
        assertThat(MessageHeaders.get(headers, MessageHeaders.TRACEPARENT))
                .matches("00-" + producer.context().traceId() + "-" + producer.context().spanId() + "-[0-9a-f]{2}");

        Span consumer = tracing.startConsumerSpan(MessagingNames.STREAM_RAW, headers);
        try (Tracer.SpanInScope ignored = tracing.inScope(consumer)) {
            assertThat(tracer.currentSpan().context().traceId()).isEqualTo(producer.context().traceId());
            // 소비 처리 중 다음 단계로 발행하면 같은 추적이 이어진다(pipeline → data2flow.telemetry)
            Map<String, Object> next = tracing.inject(new HashMap<>());
            assertThat(MessageHeaders.get(next, MessageHeaders.TRACEPARENT)).contains(producer.context().traceId());
        } finally {
            tracing.end(consumer, new IllegalStateException("처리 실패"));
        }
        assertThat(consumer.context().traceId()).isEqualTo(producer.context().traceId());
        assertThat(consumer.context().parentId()).isEqualTo(producer.context().spanId());

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertThat(spans).extracting(SpanData::getName).containsExactly("publish data2flow.raw", "process data2flow.raw");
        assertThat(spans).extracting(s -> s.getKind().name()).containsExactly("PRODUCER", "CONSUMER");
        assertThat(spans.get(1).getAttributes().asMap().toString()).contains("messaging.destination.name=data2flow.raw");
        assertThat(spans.get(1).getStatus().getStatusCode().name()).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("OPS-02.03 추적 헤더가 없는 메시지는 새 추적을 시작하고, 현재 스팬이 없으면 inject는 헤더를 바꾸지 않는다")
    void missingContext() {
        Span consumer = tracing.startConsumerSpan(MessagingNames.STREAM_TELEMETRY, Map.of());
        assertThat(consumer.context().traceId()).isNotBlank();
        assertThat(consumer.context().parentId()).isNull();
        consumer.end();
        assertThat(tracing.inject(new HashMap<>())).isEmpty();
    }

    @Test
    @DisplayName("OPS-02.03 추적을 끄면(noop) 헤더를 바꾸지 않고 메시지 처리는 그대로다")
    void noop() {
        MessageTracing off = MessageTracing.noop();
        Map<String, Object> headers = new HashMap<>(Map.of("messageId", "m"));
        Span span = off.startProducerSpan(MessagingNames.STREAM_RAW, headers);
        off.end(span, null);
        Span consumer = off.startConsumerSpan(MessagingNames.STREAM_RAW, headers);
        try (Tracer.SpanInScope ignored = off.inScope(consumer)) {
            assertThat(off.inject(headers)).containsOnlyKeys("messageId");
        }
        off.end(consumer, new RuntimeException());
    }
}
