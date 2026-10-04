package net.java21.data2flow.contracts.messaging;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
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
import net.java21.data2flow.contracts.alarm.AlarmSeverity;
import net.java21.data2flow.contracts.command.ActionIdempotencyKeys;
import net.java21.data2flow.contracts.command.CommandSource;
import net.java21.data2flow.contracts.message.ActionRequest;
import net.java21.data2flow.contracts.message.CanonicalTelemetry;
import net.java21.data2flow.contracts.message.FlowDebugMessage;
import net.java21.data2flow.contracts.message.MessageCodec;
import net.java21.data2flow.contracts.notification.NotificationEvents;
import net.java21.data2flow.contracts.notification.NotificationRecipient;
import net.java21.data2flow.contracts.notification.NotificationRequest;
import net.java21.data2flow.contracts.sink.SinkMode;
import net.java21.data2flow.contracts.sink.SinkWriteRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * M4 자동화 경로의 큐 계약을 실제 RabbitMQ 3.13에서 확인한다: 알림(NOTIFY)·Sink(SINK) 행동 요청이 종류별 Quorum 큐로 가고 본문이
 * 그대로 읽히며, 텔레메트리 처리 스팬에서 낸 행동 요청이 같은 traceId로 이어진다(NFR-07.01). 라이브 뷰 디버그 임시 큐는 1,000건을
 * 넘으면 오래된 것부터 버린다(EVT-FLW-01).
 */
@Testcontainers
class AutomationQueuesIT {

    @Container
    static final GenericContainer<?> RABBIT = new GenericContainer<>("rabbitmq:3.13-management")
            .withExposedPorts(5672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1).withStartupTimeout(Duration.ofMinutes(2)));

    private static final MessageCodec CODEC = MessageCodec.create();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-03T01:12:03Z"), ZoneOffset.UTC);
    private static Connection connection;
    private static Channel channel;

    private final SdkTracerProvider provider = SdkTracerProvider.builder().build();
    private final OpenTelemetrySdk otel = OpenTelemetrySdk.builder().setTracerProvider(provider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance())).build();
    private final OtelCurrentTraceContext context = new OtelCurrentTraceContext();
    private final Tracer tracer = new OtelTracer(otel.getTracer("it"), context, event -> { },
            new OtelBaggageManager(context, List.of(), List.of()));
    private final MessageTracing tracing = new MessageTracing(tracer,
            new OtelPropagator(otel.getPropagators(), otel.getTracer("it")));

    @BeforeAll
    static void declareTopology() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RABBIT.getHost());
        factory.setPort(RABBIT.getMappedPort(5672));
        connection = factory.newConnection("data2flow-contracts-m4-it");
        channel = connection.createChannel();
        channel.confirmSelect();
        channel.exchangeDeclare(MessagingNames.EXCHANGE_ACTIONS, "direct", true);
        channel.exchangeDeclare(MessagingNames.EXCHANGE_DLX, "direct", true);
        channel.exchangeDeclare(MessagingNames.EXCHANGE_DEBUG, "topic", true);
        for (QuorumQueueSpec q : List.of(QuorumQueueSpec.ACTION_NOTIFICATIONS, QuorumQueueSpec.ACTION_SINKS)) {
            channel.queueDeclare(q.name(), true, false, false, q.arguments());
            channel.queueBind(q.name(), q.exchange(), q.routingKey());
        }
    }

    @AfterAll
    static void close() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    private static void publish(ActionRequest request, Map<String, Object> headers) throws Exception {
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .contentType("application/json").deliveryMode(2).messageId(request.messageId().toString())
                .headers(headers).build();
        channel.basicPublish(MessagingNames.EXCHANGE_ACTIONS, request.routingKey(), true, props, CODEC.write(request));
        channel.waitForConfirmsOrDie(10_000);
    }

    private static GetResponse take(String queue) {
        AtomicReference<GetResponse> got = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(10)).until(() -> {
            got.set(channel.basicGet(queue, false));
            return got.get() != null;
        });
        return got.get();
    }

    @Test
    @DisplayName("NFR-07.01 TC-NFR-054 · RUL-03.05 TC-RUL-081 텔레메트리 처리 스팬에서 낸 알림·Sink 요청이 종류별 큐로 가고 같은 traceId로 이어진다")
    void notifyAndSinkRequestsKeepTrace() throws Exception {
        // 텔레메트리 소비(flow-engine) 스팬 안에서 행동 요청을 만든다
        CanonicalTelemetry telemetry = CanonicalTelemetry.builder().organizationId(1).sourceId(3).externalId("dev-15")
                .deviceId(15).measuredAt(CLOCK.instant()).receivedAt(CLOCK.instant()).rawMessageId(1)
                .metric(CanonicalTelemetry.Metric.of("temperature", 29.4, "°C")).build();
        Map<String, Object> streamHeaders = MessageHeaders.of(telemetry);
        Span upstream = tracing.startProducerSpan(MessagingNames.STREAM_TELEMETRY, streamHeaders);
        upstream.end();
        Span flowSpan = tracing.startConsumerSpan(MessagingNames.STREAM_TELEMETRY, streamHeaders);
        String traceId = flowSpan.context().traceId();
        String trigger = telemetry.messageId().toString();

        ActionRequest notify = ActionRequest.notify(1, ActionIdempotencyKeys.flow("f-1", "n-notify", trigger),
                CommandSource.flow("f-1", 3, "n-notify", trigger), null,
                new NotificationRequest(null, NotificationEvents.FLOW_NOTIFY, null, AlarmSeverity.WARNING, null,
                        List.of(NotificationRecipient.channelDefault("TELEGRAM")), Map.of("*", "flow.hot"),
                        Map.of("value", 29.4), null, null, null, null, null), CLOCK);
        ActionRequest sink = ActionRequest.sink(1, ActionIdempotencyKeys.flow("f-1", "n-sink", trigger, 0),
                CommandSource.flow("f-1", 3, "n-sink", trigger), null,
                new SinkWriteRequest(4, "room_temp", SinkMode.INSERT, null,
                        List.of(Map.of("device_id", 15, "temperature", 29.4)), null), CLOCK);
        try (Tracer.SpanInScope ignored = tracing.inScope(flowSpan)) {
            for (ActionRequest r : List.of(notify, sink)) {
                Map<String, Object> headers = MessageHeaders.of(r);
                Span publish = tracing.startProducerSpan(MessagingNames.EXCHANGE_ACTIONS, headers);
                publish(r, headers);
                publish.end();
            }
        } finally {
            flowSpan.end();
        }

        GetResponse n = take(QuorumQueueSpec.ACTION_NOTIFICATIONS.name());
        GetResponse s = take(QuorumQueueSpec.ACTION_SINKS.name());
        assertThat(CODEC.read(n.getBody(), ActionRequest.class).notificationRequest().event()).isEqualTo("flow.notify");
        assertThat(CODEC.read(s.getBody(), ActionRequest.class).sinkWriteRequest().records()).hasSize(1);
        for (GetResponse r : List.of(n, s)) {
            Map<String, Object> headers = new HashMap<>(r.getProps().getHeaders());
            Span actionSpan = tracing.startConsumerSpan(MessagingNames.EXCHANGE_ACTIONS, headers);
            assertThat(actionSpan.context().traceId()).as("수신부터 행동까지 같은 추적").isEqualTo(traceId);
            actionSpan.end();
            channel.basicAck(r.getEnvelope().getDeliveryTag(), false);
        }
        provider.close();
    }

    @Test
    @DisplayName("FLW-03.01 TC-FLW-066 EVT-FLW-01 디버그 임시 큐는 flow.{flowId}만 받고 1,000건을 넘으면 오래된 것부터 버린다")
    void debugQueueDropsHead() throws Exception {
        String queue = channel.queueDeclare("", false, true, true, MessagingNames.debugQueueArguments()).getQueue();
        channel.queueBind(queue, MessagingNames.EXCHANGE_DEBUG, MessagingNames.debugRoutingKey("f-7f3a"));
        Instant t = CLOCK.instant();
        for (int i = 0; i < MessagingNames.DEBUG_QUEUE_MAX_LENGTH + 50; i++) {
            FlowDebugMessage m = FlowDebugMessage.stats("f-7f3a", "engine-0", t.plusSeconds(i), 13, List.of());
            channel.basicPublish(MessagingNames.EXCHANGE_DEBUG, m.routingKey(), null, CODEC.write(m));
        }
        FlowDebugMessage other = FlowDebugMessage.stats("f-other", "engine-0", t, 1, List.of());
        channel.basicPublish(MessagingNames.EXCHANGE_DEBUG, other.routingKey(), null, CODEC.write(other));
        channel.waitForConfirmsOrDie(10_000);
        await().atMost(Duration.ofSeconds(10))
                .until(() -> channel.messageCount(queue) == MessagingNames.DEBUG_QUEUE_MAX_LENGTH);
        GetResponse oldest = channel.basicGet(queue, true);
        FlowDebugMessage first = CODEC.read(oldest.getBody(), FlowDebugMessage.class);
        assertThat(first.flowId()).isEqualTo("f-7f3a");
        assertThat(first.t()).isEqualTo(t.plusSeconds(50));
    }
}
