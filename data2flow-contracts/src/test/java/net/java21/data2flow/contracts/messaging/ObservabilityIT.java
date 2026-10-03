package net.java21.data2flow.contracts.messaging;

import com.rabbitmq.stream.Address;
import com.rabbitmq.stream.Consumer;
import com.rabbitmq.stream.Environment;
import com.rabbitmq.stream.OffsetSpecification;
import com.rabbitmq.stream.Producer;
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
import net.java21.data2flow.contracts.message.CanonicalTelemetry;
import net.java21.data2flow.contracts.message.MessageCodec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * OPS-02.03 TC-OPS-021(메시지 구간)·ING-05.01 TC-ING-063(라우팅): 실제 RabbitMQ 3.13 Super Stream에서
 * 계약(MessageCodec·MessageHeaders·StreamRoutingKeys·MessageTracing)이 그대로 통한다.
 *
 * <p>pipeline이 {@code data2flow.telemetry}에 발행하고 flow-engine 그룹이 읽는 구간을 그대로 흉내 낸다. 서비스 전 구간 추적
 * (ingress → action, AT-OPS-04.2)은 각 서비스 IT와 staging에서 확인한다.
 */
@Testcontainers
class ObservabilityIT {

    private static final int STREAM_PORT = 5552;

    @Container
    static final GenericContainer<?> RABBIT = new GenericContainer<>("rabbitmq:3.13-management")
            .withExposedPorts(STREAM_PORT, 5672)
            .withCopyToContainer(Transferable.of("[rabbitmq_management,rabbitmq_stream]."), "/etc/rabbitmq/enabled_plugins")
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1).withStartupTimeout(Duration.ofMinutes(2)));

    private static Environment environment;
    private static final MessageCodec CODEC = MessageCodec.create();

    private final SdkTracerProvider provider = SdkTracerProvider.builder().build();
    private final OpenTelemetrySdk otel = OpenTelemetrySdk.builder().setTracerProvider(provider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance())).build();
    private final OtelCurrentTraceContext context = new OtelCurrentTraceContext();
    private final Tracer tracer = new OtelTracer(otel.getTracer("it"), context, event -> { },
            new OtelBaggageManager(context, List.of(), List.of()));
    private final MessageTracing tracing = new MessageTracing(tracer,
            new OtelPropagator(otel.getPropagators(), otel.getTracer("it")));

    @BeforeAll
    static void createSuperStream() {
        String host = RABBIT.getHost();
        int port = RABBIT.getMappedPort(STREAM_PORT);
        environment = Environment.builder().host(host).port(port).username("guest").password("guest")
                .addressResolver(address -> new Address(host, port)).build();
        SuperStreamSpec spec = SuperStreamSpec.TELEMETRY;
        environment.streamCreator().name(spec.name()).maxAge(spec.maxAge())
                .superStream().partitions(spec.partitions()).creator().create();
    }

    @AfterAll
    static void close() {
        if (environment != null) {
            environment.close();
        }
    }

    record Received(String partition, CanonicalTelemetry telemetry, Map<String, Object> headers, String consumerTraceId) {
    }

    @Test
    @DisplayName("OPS-02.03 TC-OPS-021 · ING-05.01 TC-ING-063 같은 기기는 같은 파티션에서 순서대로, 헤더와 traceparent가 이어진다")
    void telemetryHopKeepsPartitionOrderHeadersAndTrace() {
        List<Received> received = new CopyOnWriteArrayList<>();
        Consumer consumer = environment.consumerBuilder()
                .superStream(MessagingNames.STREAM_TELEMETRY)
                .name(ConsumerGroups.of(ConsumerGroups.FLOW, null))
                .singleActiveConsumer()
                .offset(OffsetSpecification.first())
                .messageHandler((ctx, message) -> {
                    Map<String, Object> headers = new HashMap<>(message.getApplicationProperties());
                    Span span = tracing.startConsumerSpan(MessagingNames.STREAM_TELEMETRY, headers);
                    try (Tracer.SpanInScope ignored = tracing.inScope(span)) {
                        CanonicalTelemetry t = CODEC.read(message.getBodyAsBinary(), CanonicalTelemetry.class);
                        received.add(new Received(ctx.stream(), t, headers, tracer.currentSpan().context().traceId()));
                    } finally {
                        span.end();
                    }
                })
                .build();

        Producer producer = environment.producerBuilder()
                .superStream(MessagingNames.STREAM_TELEMETRY)
                .routing(message -> CODEC.read(message.getBodyAsBinary(), CanonicalTelemetry.class).routingKey())
                .producerBuilder()
                .build();

        Map<String, String> producerTraceIds = new ConcurrentHashMap<>();
        Set<String> confirmed = ConcurrentHashMap.newKeySet();
        Instant t0 = Instant.parse("2026-10-03T02:40:00Z");
        for (int seq = 0; seq < 30; seq++) {
            long deviceId = 17 + (seq % 5);
            CanonicalTelemetry telemetry = CanonicalTelemetry.builder().organizationId(1).sourceId(3)
                    .externalId("dev-" + deviceId).deviceId(deviceId)
                    .measuredAt(t0.plusSeconds(seq)).receivedAt(t0.plusSeconds(seq))
                    .metric(CanonicalTelemetry.Metric.of("seq", seq, null)).rawMessageId(1000 + seq).build();
            Map<String, Object> headers = MessageHeaders.of(telemetry);
            Span span = tracing.startProducerSpan(MessagingNames.STREAM_TELEMETRY, headers);
            producerTraceIds.put(telemetry.messageId().toString(), span.context().traceId());
            var builder = producer.messageBuilder().addData(CODEC.write(telemetry))
                    .properties().messageId(telemetry.messageId().toString()).messageBuilder()
                    .applicationProperties();
            headers.forEach((k, v) -> builder.entry(k, v.toString()));
            producer.send(builder.messageBuilder().build(), status -> {
                tracing.end(span, status.isConfirmed() ? null : new IllegalStateException("confirm 실패"));
                if (status.isConfirmed()) {
                    confirmed.add(telemetry.messageId().toString());
                }
            });
        }

        await().atMost(Duration.ofSeconds(30)).until(() -> confirmed.size() == 30 && received.size() == 30);
        producer.close();
        consumer.close();

        Map<Long, List<Received>> byDevice = received.stream()
                .collect(Collectors.groupingBy(r -> r.telemetry().deviceId()));
        assertThat(byDevice).hasSize(5);
        byDevice.forEach((deviceId, list) -> {
            assertThat(list).extracting(Received::partition).as("기기 %d 파티션", deviceId).containsOnly(list.getFirst().partition());
            assertThat(list).extracting(r -> r.telemetry().metric("seq").value())
                    .as("기기 %d 순서", deviceId).isSorted();
        });
        for (Received r : received) {
            String messageId = r.telemetry().messageId().toString();
            assertThat(r.headers()).containsEntry(MessageHeaders.MESSAGE_ID, messageId)
                    .containsEntry(MessageHeaders.SCHEMA_VERSION, "1")
                    .containsEntry(MessageHeaders.SCHEMA, "canonical-telemetry")
                    .containsEntry(MessageHeaders.ORGANIZATION_ID, "1")
                    .containsKey(MessageHeaders.TRACEPARENT);
            assertThat(r.consumerTraceId()).as("추적 연결").isEqualTo(producerTraceIds.get(messageId));
            assertThat(r.partition()).startsWith(MessagingNames.STREAM_TELEMETRY + "-");
        }
        provider.close();
    }
}
