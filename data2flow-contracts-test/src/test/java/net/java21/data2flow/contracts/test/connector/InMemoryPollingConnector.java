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
import net.java21.data2flow.contracts.connector.LeaseLostException;
import net.java21.data2flow.contracts.connector.PayloadFormat;
import net.java21.data2flow.contracts.connector.PollCursor;
import net.java21.data2flow.contracts.connector.RawSink;
import net.java21.data2flow.contracts.connector.ScalingMode;
import net.java21.data2flow.contracts.connector.SourceConfig;
import net.java21.data2flow.contracts.connector.SourceConnector;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 키트의 CURSOR 시나리오를 시험하는 메모리 폴링 커넥터(DSC-09.09). 상대편은 {@link Server}(추가만 되는 레코드 목록, 커서 = 다음 읽을
 * 위치)다. 한 페이지를 모두 기록(confirm)한 뒤에만 커서를 저장하고(BR-DSC-24), 기록에 실패하면 저장하지 않고 같은 위치부터 다시 읽는다.
 */
final class InMemoryPollingConnector implements SourceConnector {

    /** 상대편 API 서버 겸 확인 수(= 저장된 커서 위치) */
    static final class Server implements ContractPeer {
        private final List<byte[]> records = new CopyOnWriteArrayList<>();
        private final LinkedBlockingQueue<Boolean> signal = new LinkedBlockingQueue<>();
        private final InMemoryPollCursorStore store;
        private final long sourceId;

        Server(InMemoryPollCursorStore store, long sourceId) {
            this.store = store;
            this.sourceId = sourceId;
        }

        @Override
        public void publish(List<byte[]> payloads) {
            records.addAll(payloads);
            signal.offer(Boolean.TRUE);
        }

        @Override
        public long acknowledgedCount() {
            return store.load(sourceId).map(c -> Long.parseLong(c.cursor())).orElse(0L);
        }

        List<byte[]> page(int from, int size) {
            int to = Math.min(records.size(), from + size);
            return from >= to ? List.of() : new ArrayList<>(records.subList(from, to));
        }

        void awaitChange() throws InterruptedException {
            signal.poll(20, TimeUnit.MILLISECONDS);
        }
    }

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private final Server server;

    InMemoryPollingConnector(Server server) {
        this.server = server;
    }

    @Override
    public ConnectorDescriptor descriptor() {
        return new ConnectorDescriptor("in-memory-poll", "In-memory polling", "1.0.0", ConnectorCategory.HTTP,
                Set.of(AuthMethod.TOKEN), Set.of(PayloadFormat.JSON), AckMode.CURSOR, ScalingMode.SINGLETON, false);
    }

    @Override
    public JsonNode configSchema() {
        return MAPPER.readTree("{\"type\":\"object\",\"properties\":{\"intervalSec\":{\"type\":\"integer\",\"minimum\":10}}}");
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
        private static final int PAGE = 4;
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
                        server.awaitChange();
                        continue;
                    }
                    PollCursor cursor = ctx.cursorStore().load(config.sourceId()).orElse(PollCursor.at("0"));
                    int from = Integer.parseInt(cursor.cursor());
                    List<byte[]> page = server.page(from, PAGE);
                    if (page.isEmpty()) {
                        server.awaitChange();
                        continue;
                    }
                    List<CompletableFuture<Void>> writes = new ArrayList<>();
                    for (byte[] payload : page) {
                        writes.add(sink.write(ctx.envelope(config, "poll/records", payload)).toCompletableFuture());
                    }
                    try {
                        CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join();
                    } catch (RuntimeException failed) {
                        continue;   // 커서를 저장하지 않고 같은 위치부터 다시 읽는다
                    }
                    received.addAndGet(page.size());
                    ctx.cursorStore().save(config.sourceId(), PollCursor.at(Integer.toString(from + page.size())));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (LeaseLostException e) {
                    running = false;
                    state = ConnectorState.DISCONNECTED;
                    return;
                }
            }
        }

        @Override
        public void pause() {
            paused = true;
        }

        @Override
        public void resume() {
            paused = false;
            server.signal.offer(Boolean.TRUE);
        }

        @Override
        public ConnectorStatus status() {
            return new ConnectorStatus(state, null, null, null, null, 0, received.get(), null);
        }

        @Override
        public void close() {
            running = false;
            if (worker != null) {
                worker.interrupt();
            }
            state = ConnectorState.DISCONNECTED;
        }
    }
}
