package net.java21.data2flow.contracts.messaging;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import net.java21.data2flow.contracts.command.ActionIdempotencyKeys;
import net.java21.data2flow.contracts.command.ActionKind;
import net.java21.data2flow.contracts.command.CommandPayload;
import net.java21.data2flow.contracts.command.CommandPriority;
import net.java21.data2flow.contracts.command.CommandSource;
import net.java21.data2flow.contracts.command.CommandTarget;
import net.java21.data2flow.contracts.message.ActionRequest;
import net.java21.data2flow.contracts.message.MessageCodec;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * ACT-02.01·ADR-020 (architecture.md §4.3): 실제 RabbitMQ 3.13에서 {@link QuorumQueueSpec} 정의대로 선언한 행동 큐가 ActionRequest를
 * 종류별로 받고, 5번 넘게 실패한 요청은 DLX {@code data2flow.dlx}를 거쳐 {@code action.commands.dlq}로 간다.
 */
@Testcontainers
class ActionQueueIT {

    @Container
    static final GenericContainer<?> RABBIT = new GenericContainer<>("rabbitmq:3.13-management")
            .withExposedPorts(5672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1).withStartupTimeout(Duration.ofMinutes(2)));

    private static final MessageCodec CODEC = MessageCodec.create();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-03T01:12:03Z"), ZoneOffset.UTC);
    private static Connection connection;
    private static Channel channel;

    @BeforeAll
    static void declareTopology() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RABBIT.getHost());
        factory.setPort(RABBIT.getMappedPort(5672));
        connection = factory.newConnection("data2flow-contracts-it");
        channel = connection.createChannel();
        channel.confirmSelect();
        channel.exchangeDeclare(MessagingNames.EXCHANGE_ACTIONS, "direct", true);
        channel.exchangeDeclare(MessagingNames.EXCHANGE_DLX, "direct", true);
        for (QuorumQueueSpec q : List.of(QuorumQueueSpec.ACTION_COMMANDS, QuorumQueueSpec.ACTION_NOTIFICATIONS,
                QuorumQueueSpec.ACTION_SINKS)) {
            channel.queueDeclare(q.name(), true, false, false, q.arguments());
            channel.queueBind(q.name(), q.exchange(), q.routingKey());
            channel.queueDeclare(q.deadLetterQueue(), true, false, false, QuorumQueueSpec.deadLetterArguments());
            channel.queueBind(q.deadLetterQueue(), MessagingNames.EXCHANGE_DLX, q.name());
        }
    }

    @AfterAll
    static void close() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    private static void publish(ActionRequest request) throws Exception {
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .contentType("application/json").deliveryMode(2).messageId(request.messageId().toString())
                .headers(MessageHeaders.of(request)).build();
        channel.basicPublish(MessagingNames.EXCHANGE_ACTIONS, request.routingKey(), true, props, CODEC.write(request));
        channel.waitForConfirmsOrDie(10_000);
    }

    private static ActionRequest command(String trigger) {
        CommandSource source = CommandSource.flow("f-7f3a", 13, "n-act-1", trigger);
        return ActionRequest.command(1, ActionIdempotencyKeys.flow("f-7f3a", "n-act-1", trigger), source, null,
                new CommandPayload(CommandTarget.device(15), "Thermostat", "set", Map.of("mode", "cool"), true), CLOCK);
    }

    @Test
    @DisplayName("ACT-02.01 TC-ACT-027 행동 종류별 라우팅: COMMAND → action.commands, NOTIFY → action.notifications, 본문·헤더 그대로")
    void routesByKind() throws Exception {
        ActionRequest command = command("m-route");
        publish(command);
        ActionRequest notify = ActionRequest.of(ActionKind.NOTIFY, 1, "n-route", CommandSource.system(), CommandPriority.SAFETY,
                null, CODEC.mapper().createObjectNode().put("alarmId", 5), CLOCK);
        publish(notify);

        AtomicReference<GetResponse> got = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(10)).until(() -> {
            got.set(channel.basicGet(QuorumQueueSpec.ACTION_COMMANDS.name(), false));
            return got.get() != null;
        });
        ActionRequest read = CODEC.read(got.get().getBody(), ActionRequest.class);
        assertThat(read).isEqualTo(command);
        assertThat(got.get().getProps().getHeaders().get(MessageHeaders.SCHEMA).toString()).isEqualTo("action-request");
        channel.basicAck(got.get().getEnvelope().getDeliveryTag(), false);

        await().atMost(Duration.ofSeconds(10)).until(() -> {
            got.set(channel.basicGet(QuorumQueueSpec.ACTION_NOTIFICATIONS.name(), false));
            return got.get() != null;
        });
        assertThat(CODEC.read(got.get().getBody(), ActionRequest.class).kind()).isEqualTo(ActionKind.NOTIFY);
        channel.basicAck(got.get().getEnvelope().getDeliveryTag(), false);
    }

    @Test
    @DisplayName("ACT-02.01 ADR-020 처리 실패(NACK 재시도)가 delivery-limit 5를 넘으면 action.commands.dlq로 옮겨지고 본문은 그대로다")
    void exceedingDeliveryLimitDeadLetters() throws Exception {
        ActionRequest poison = command("m-poison");
        publish(poison);
        AtomicInteger deliveries = new AtomicInteger();
        AtomicReference<GetResponse> dead = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(50)).until(() -> {
            GetResponse r = channel.basicGet(QuorumQueueSpec.ACTION_COMMANDS.name(), false);
            if (r != null) {
                deliveries.incrementAndGet();
                channel.basicNack(r.getEnvelope().getDeliveryTag(), false, true);
            }
            dead.set(channel.basicGet(QuorumQueueSpec.ACTION_COMMANDS.deadLetterQueue(), false));
            return dead.get() != null;
        });
        assertThat(deliveries.get()).isBetween(MessagingNames.DELIVERY_LIMIT, MessagingNames.DELIVERY_LIMIT + 1);
        assertThat(CODEC.read(dead.get().getBody(), ActionRequest.class)).isEqualTo(poison);
        assertThat(dead.get().getProps().getMessageId()).isEqualTo(poison.messageId().toString());
        channel.basicAck(dead.get().getEnvelope().getDeliveryTag(), false);
        assertThat(channel.messageCount(QuorumQueueSpec.ACTION_COMMANDS.name())).isZero();
    }
}
