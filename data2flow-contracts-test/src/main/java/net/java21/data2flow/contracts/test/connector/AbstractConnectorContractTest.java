package net.java21.data2flow.contracts.test.connector;

import net.java21.data2flow.contracts.connector.AckMode;
import net.java21.data2flow.contracts.connector.ConnectionTestResult;
import net.java21.data2flow.contracts.connector.ConnectorCatalogEntry;
import net.java21.data2flow.contracts.connector.ConnectorContext;
import net.java21.data2flow.contracts.connector.ConnectorDescriptor;
import net.java21.data2flow.contracts.connector.ConnectorSession;
import net.java21.data2flow.contracts.connector.ConnectorState;
import net.java21.data2flow.contracts.connector.ConnectorStatus;
import net.java21.data2flow.contracts.connector.SourceConfig;
import net.java21.data2flow.contracts.connector.SourceConnector;
import net.java21.data2flow.contracts.message.RawEnvelope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 커넥터 계약 테스트 키트(DSC-09.02, BR-DSC-23). 모든 커넥터는 이 키트를 통과해야 카탈로그에 등록된다.
 *
 * <p>커넥터 저장소(ingress)는 커넥터마다 이 클래스를 상속한 {@code *ContractIT}를 만들고, Testcontainers로 띄운 실제 상대에 맞춰
 * {@link #connector()}, {@link #sourceConfig()}, {@link #peer()}를 채운다(TC-DSC-237~251). 키트가 확인하는 공통 시나리오:
 * <ol>
 *   <li>설명·설정 스키마·카탈로그 항목이 완전하다</li>
 *   <li>연결 테스트가 단계별 결과를 돌려주고 성공한다(DSC-09.11)</li>
 *   <li>메시지 {@link #messageCount()}건(기본 1,000)을 빠짐없이 원본 봉투(RawEnvelope v1)로 넘긴다</li>
 *   <li><b>기록이 끝나기 전에는 상대에게 확인하지 않는다</b>(AT-DSC-18.1, DSC-09.03) — 확인 방식이 AFTER_WRITE·CURSOR일 때</li>
 *   <li>기록에 실패한 메시지는 확인하지 않고 다시 받아 결국 모두 기록한다(무손실)</li>
 *   <li>일시정지 동안은 넘기지 않고, 재개하면 멈춘 동안의 메시지까지 넘긴다</li>
 *   <li>상태를 보고하고, 닫으면 연결을 끊는다(두 번 닫아도 된다)</li>
 * </ol>
 *
 * <pre>{@code
 * @Testcontainers
 * class Mqtt5ConnectorContractIT extends AbstractConnectorContractTest {
 *     @Container static final GenericContainer<?> EMQX = ...;
 *     protected SourceConnector connector() { return new MqttConnector(); }
 *     protected SourceConfig sourceConfig() { return new SourceConfig(1, 3, "CONNECTOR", "mqtt", config, secrets, "kit-0"); }
 *     protected ContractPeer peer() { return mqttPeer; }
 * }
 * }</pre>
 */
public abstract class AbstractConnectorContractTest {

    /** 키트가 쓰는 인스턴스 이름({@code RawEnvelope.ingressInstance}) */
    public static final String INSTANCE_ID = "contract-kit-0";

    private RecordingRawSink sink;
    private ConnectorSession session;
    private final List<ConnectorStatus> reported = new CopyOnWriteArrayList<>();
    private final String runId = UUID.randomUUID().toString().substring(0, 8);
    private int sequence;

    /** 시험할 커넥터 */
    protected abstract SourceConnector connector();

    /** 테스트 상대에 접속하는 소스 설정 */
    protected abstract SourceConfig sourceConfig();

    /** 테스트 상대 */
    protected abstract ContractPeer peer();

    /** 수신 시나리오 메시지 수(TC-DSC-237~251: 1,000건) */
    protected int messageCount() {
        return 1000;
    }

    /** 한 시나리오의 최대 대기 시간 */
    protected Duration timeout() {
        return Duration.ofSeconds(60);
    }

    /** "아무 일도 일어나지 않아야 하는" 구간의 길이(확인 금지·일시정지 확인) */
    protected Duration quietPeriod() {
        return Duration.ofMillis(500);
    }

    protected Clock clock() {
        return Clock.systemUTC();
    }

    @BeforeEach
    void openSession() {
        sink = new RecordingRawSink();
        session = connector().open(sourceConfig(), sink, new ConnectorContext(INSTANCE_ID, clock(), reported::add));
    }

    @AfterEach
    void closeSession() {
        if (session != null) {
            session.close();
        }
    }

    protected RecordingRawSink sink() {
        return sink;
    }

    protected ConnectorSession session() {
        return session;
    }

    @Test
    @DisplayName("DSC-09.02 TC-DSC-235 커넥터 설명·설정 스키마·카탈로그 항목이 완전하다")
    void descriptorSchemaAndCatalogEntryAreComplete() {
        ConnectorDescriptor d = connector().descriptor();
        assertThat(d).as("descriptor").isNotNull();
        assertThat(d.key()).matches(ConnectorDescriptor.KEY);
        assertThat(d.version()).matches(ConnectorDescriptor.SEMVER);
        JsonNode schema = connector().configSchema();
        assertThat(schema).as("configSchema").isNotNull();
        assertThat(schema.isObject()).as("configSchema는 JSON 객체").isTrue();
        assertThat(schema.path("type").asString("")).as("configSchema.type").isEqualTo("object");
        ConnectorCatalogEntry entry = connector().catalogEntry();
        assertThat(entry.key()).isEqualTo(d.key());
        assertThat(entry.version()).isEqualTo(d.version());
        assertThat(entry.ackMode()).isEqualTo(d.ackMode());
        assertThat(entry.scaling()).isEqualTo(d.scaling());
        assertThat(entry.schema()).isEqualTo(schema);
    }

    @Test
    @DisplayName("DSC-09.11 연결 테스트가 단계별 결과를 돌려주고 테스트 상대에는 성공한다")
    void connectionTestSucceeds() {
        ConnectionTestResult result = connector().test(sourceConfig());
        assertThat(result.steps()).as("단계별 결과").isNotEmpty();
        assertThat(result.failedStep()).as("실패 단계").isEmpty();
        assertThat(result.preview()).hasSizeLessThanOrEqualTo(ConnectionTestResult.MAX_PREVIEW);
    }

    @Test
    @DisplayName("DSC-09.02 TC-DSC-237 메시지를 빠짐없이 RawEnvelope v1로 넘긴다(소스·인스턴스·수신 시각·중복 키)")
    void deliversEveryMessageAsRawEnvelope() throws Exception {
        startAndAwaitConnected();
        List<byte[]> payloads = payloads(messageCount());
        peer().publish(payloads);
        Set<String> expected = asText(payloads);
        await().atMost(timeout()).until(() -> asText(sink.written().stream().map(RawEnvelope::payload).toList())
                .containsAll(expected));

        SourceConfig cfg = sourceConfig();
        Set<java.util.UUID> messageIds = new HashSet<>();
        for (RawEnvelope e : sink.written()) {
            assertThat(e.v()).isEqualTo(RawEnvelope.VERSION);
            assertThat(e.organizationId()).isEqualTo(cfg.organizationId());
            assertThat(e.sourceId()).isEqualTo(cfg.sourceId());
            assertThat(e.sourceType()).isEqualTo(cfg.sourceType());
            assertThat(e.ingressInstance()).isEqualTo(INSTANCE_ID);
            assertThat(e.receivedAt()).isNotNull();
            assertThat(e.dedupKey()).isNotBlank();
            assertThat(messageIds.add(e.messageId())).as("messageId 중복: " + e.messageId()).isTrue();
        }
        assertThat(session.status().received()).isGreaterThanOrEqualTo(messageCount());
    }

    @Test
    @DisplayName("DSC-09.03 AT-DSC-18.1 기록(confirm)이 끝나기 전에는 상대에게 확인하지 않는다")
    void acknowledgesOnlyAfterWrite() throws Exception {
        assumeAcknowledging();
        startAndAwaitConnected();
        long before = peer().acknowledgedCount();
        sink.hold();
        List<byte[]> payloads = payloads(10);
        peer().publish(payloads);
        await().atMost(timeout()).until(() -> sink.heldCount() >= 1);
        await().during(quietPeriod()).atMost(quietPeriod().plus(timeout()))
                .until(() -> peer().acknowledgedCount() == before);

        sink.release();
        await().atMost(timeout()).until(() -> peer().acknowledgedCount() - before >= payloads.size());
        assertThat(asText(sink.written().stream().map(RawEnvelope::payload).toList())).containsAll(asText(payloads));
    }

    @Test
    @DisplayName("DSC-09.03 TC-ING-016 기록에 실패한 메시지는 확인하지 않고 다시 받아 결국 모두 기록한다")
    void failedWritesAreNotAcknowledgedAndAreRedelivered() throws Exception {
        assumeAcknowledging();
        startAndAwaitConnected();
        long before = peer().acknowledgedCount();
        sink.failNext(3);
        List<byte[]> payloads = payloads(10);
        peer().publish(payloads);
        Set<String> expected = asText(payloads);
        await().atMost(timeout()).until(() -> asText(sink.written().stream().map(RawEnvelope::payload).toList())
                .containsAll(expected));
        assertThat(sink.attempts()).isGreaterThanOrEqualTo(payloads.size() + 3);
        await().atMost(timeout()).until(() -> peer().acknowledgedCount() - before >= payloads.size());
    }

    @Test
    @DisplayName("DSC-09.02 일시정지 동안은 넘기지 않고, 재개하면 멈춘 동안의 메시지까지 넘긴다")
    void pauseStopsDeliveryAndResumeCatchesUp() throws Exception {
        startAndAwaitConnected();
        session.pause();
        int before = sink.attempts();
        List<byte[]> payloads = payloads(10);
        peer().publish(payloads);
        await().during(quietPeriod()).atMost(quietPeriod().plus(timeout())).until(() -> sink.attempts() == before);
        session.resume();
        Set<String> expected = asText(payloads);
        await().atMost(timeout()).until(() -> asText(sink.written().stream().map(RawEnvelope::payload).toList())
                .containsAll(expected));
    }

    @Test
    @DisplayName("DSC-02.04 EVT-DSC-02 상태를 보고하고, 닫으면 연결을 끊는다(두 번 닫아도 된다)")
    void reportsStatusAndCloses() {
        startAndAwaitConnected();
        assertThat(reported).extracting(ConnectorStatus::state).contains(ConnectorState.CONNECTED);
        session.close();
        assertThat(session.status().state()).isIn(ConnectorState.DISCONNECTED, ConnectorState.DISABLED);
        session.close();
    }

    /** 세션을 시작하고 CONNECTED가 될 때까지 기다린다 */
    protected void startAndAwaitConnected() {
        session.start();
        await().atMost(timeout()).until(() -> session.status().state() == ConnectorState.CONNECTED);
    }

    /** 이 실행에서만 쓰는 고유 payload(JSON). 이전 시나리오의 잔여 메시지와 섞이지 않는다 */
    protected List<byte[]> payloads(int count) {
        List<byte[]> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(("{\"contractKit\":\"" + runId + "\",\"seq\":" + (sequence++) + "}").getBytes(StandardCharsets.UTF_8));
        }
        return list;
    }

    private void assumeAcknowledging() {
        AckMode mode = connector().descriptor().ackMode();
        Assumptions.assumeTrue(mode == AckMode.AFTER_WRITE || mode == AckMode.CURSOR,
                "확인 방식이 " + mode + "인 커넥터는 확인 시점 시나리오가 없다(화면에 유실 가능 표시)");
    }

    private static Set<String> asText(List<byte[]> payloads) {
        Set<String> set = new HashSet<>();
        payloads.forEach(p -> set.add(new String(p, StandardCharsets.UTF_8)));
        return set;
    }
}
