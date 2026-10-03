package net.java21.data2flow.contracts.test.connector;

import net.java21.data2flow.contracts.connector.AckMode;
import net.java21.data2flow.contracts.connector.AuthMethod;
import net.java21.data2flow.contracts.connector.ConnectionTestResult;
import net.java21.data2flow.contracts.connector.ConnectorCategory;
import net.java21.data2flow.contracts.connector.ConnectorContext;
import net.java21.data2flow.contracts.connector.ConnectorDescriptor;
import net.java21.data2flow.contracts.connector.ConnectorSession;
import net.java21.data2flow.contracts.connector.ConnectorState;
import net.java21.data2flow.contracts.connector.ConnectorStatus;
import net.java21.data2flow.contracts.connector.PayloadFormat;
import net.java21.data2flow.contracts.connector.RawSink;
import net.java21.data2flow.contracts.connector.ScalingMode;
import net.java21.data2flow.contracts.connector.SourceConfig;
import net.java21.data2flow.contracts.connector.SourceConnector;
import net.java21.data2flow.contracts.message.RawEnvelope;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 키트 자체를 시험하는 메모리 커넥터. 상대편은 {@link Broker}(미확인 메시지를 보관하는 큐)다.
 *
 * <p>{@link Behavior#CORRECT}는 기록이 끝난 뒤 확인하고 실패하면 다시 받는다. {@link Behavior#ACK_BEFORE_WRITE}는 받자마자 확인하고
 * (AT-DSC-18.1 위반), {@link Behavior#DROP_ON_FAILURE}는 기록에 실패해도 확인해 버린다(유실).
 */
final class InMemoryConnector implements SourceConnector {

    enum Behavior { CORRECT, ACK_BEFORE_WRITE, DROP_ON_FAILURE }

    /** 상대편 브로커: 보낸 메시지를 보관하고, 확인받지 못한 메시지는 다시 준다 */
    static final class Broker implements ContractPeer {
        private final LinkedBlockingDeque<byte[]> queue = new LinkedBlockingDeque<>();
        private final AtomicLong acked = new AtomicLong();

        @Override
        public void publish(List<byte[]> payloads) {
            queue.addAll(payloads);
        }

        @Override
        public long acknowledgedCount() {
            return acked.get();
        }

        byte[] take() throws InterruptedException {
            return queue.poll(20, TimeUnit.MILLISECONDS);
        }

        void ack() {
            acked.incrementAndGet();
        }

        void redeliver(byte[] payload) {
            queue.addFirst(payload);
        }
    }

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private final Broker broker;
    private final Behavior behavior;

    InMemoryConnector(Broker broker, Behavior behavior) {
        this.broker = broker;
        this.behavior = behavior;
    }

    @Override
    public ConnectorDescriptor descriptor() {
        return new ConnectorDescriptor("in-memory", "In-memory", "1.0.0", ConnectorCategory.QUEUE, Set.of(AuthMethod.NONE),
                Set.of(PayloadFormat.JSON), AckMode.AFTER_WRITE, ScalingMode.SCALABLE, false);
    }

    @Override
    public JsonNode configSchema() {
        return MAPPER.readTree("{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":\"object\"}");
    }

    @Override
    public ConnectionTestResult test(SourceConfig config) {
        return new ConnectionTestResult(List.of(ConnectionTestResult.Step.ok(ConnectionTestResult.STEP_SUBSCRIBE, 1)),
                List.of(), false);
    }

    @Override
    public ConnectorSession open(SourceConfig config, RawSink sink, ConnectorContext ctx) {
        return new Session(config, sink, ctx);
    }

    private final class Session implements ConnectorSession {
        private final SourceConfig config;
        private final RawSink sink;
        private final ConnectorContext ctx;
        private final AtomicLong received = new AtomicLong();
        private volatile ConnectorState state = ConnectorState.DISCONNECTED;
        private volatile boolean paused;
        private volatile boolean running;
        private Thread worker;

        Session(SourceConfig config, RawSink sink, ConnectorContext ctx) {
            this.config = config;
            this.sink = sink;
            this.ctx = ctx;
        }

        @Override
        public void start() {
            running = true;
            worker = Thread.ofVirtual().start(this::loop);
            state = ConnectorState.CONNECTED;
            ctx.reportStatus(status());
        }

        private void loop() {
            while (running) {
                try {
                    if (paused) {
                        synchronized (this) {
                            while (paused && running) {
                                wait();
                            }
                        }
                        continue;
                    }
                    byte[] payload = broker.take();
                    if (payload == null) {
                        continue;
                    }
                    if (paused || !running) {
                        broker.redeliver(payload);
                        continue;
                    }
                    RawEnvelope envelope = ctx.envelope(config, "in-memory/topic", payload);
                    if (behavior == Behavior.ACK_BEFORE_WRITE) {
                        broker.ack();
                    }
                    sink.write(envelope).whenComplete((ok, error) -> {
                        if (error == null) {
                            received.incrementAndGet();
                            if (behavior != Behavior.ACK_BEFORE_WRITE) {
                                broker.ack();
                            }
                        } else if (behavior == Behavior.DROP_ON_FAILURE) {
                            broker.ack();
                        } else if (behavior == Behavior.CORRECT) {
                            broker.redeliver(payload);
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        @Override
        public void pause() {
            paused = true;
        }

        @Override
        public synchronized void resume() {
            paused = false;
            notifyAll();
        }

        @Override
        public ConnectorStatus status() {
            return new ConnectorStatus(state, null, null, null, null, 0, received.get(), null);
        }

        @Override
        public void close() {
            running = false;
            synchronized (this) {
                notifyAll();
            }
            if (worker != null) {
                worker.interrupt();
            }
            state = ConnectorState.DISCONNECTED;
        }
    }
}
