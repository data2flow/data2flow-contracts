package net.java21.data2flow.contracts.test.connector;

import net.java21.data2flow.contracts.connector.SourceConfig;
import net.java21.data2flow.contracts.connector.SourceConnector;
import net.java21.data2flow.contracts.message.RawEnvelope;
import net.java21.data2flow.contracts.message.SourceTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.testkit.engine.EventConditions.event;
import static org.junit.platform.testkit.engine.EventConditions.finishedSuccessfully;
import static org.junit.platform.testkit.engine.EventConditions.finishedWithFailure;
import static org.junit.platform.testkit.engine.EventConditions.test;

/** DSC-09.02 AT-DSC-18.1: 키트는 계약을 어기는 커넥터를 떨어뜨린다(카탈로그 등록 불가 → 빌드 실패) */
class ConnectorContractKitTest {

    /** 받자마자 확인하는 커넥터(기록 전 확인 금지 위반). 정적 중첩 클래스라 빌드가 직접 실행하지 않는다 */
    static class EagerAckRun extends AbstractConnectorContractTest {
        private final InMemoryConnector.Broker broker = new InMemoryConnector.Broker();

        @Override
        protected SourceConnector connector() {
            return new InMemoryConnector(broker, InMemoryConnector.Behavior.ACK_BEFORE_WRITE);
        }

        @Override
        protected SourceConfig sourceConfig() {
            return config();
        }

        @Override
        protected ContractPeer peer() {
            return broker;
        }

        @Override
        protected java.time.Duration timeout() {
            return java.time.Duration.ofSeconds(3);
        }
    }

    /** 기록에 실패해도 확인해 버리는 커넥터(유실) */
    static class DropOnFailureRun extends AbstractConnectorContractTest {
        private final InMemoryConnector.Broker broker = new InMemoryConnector.Broker();

        @Override
        protected SourceConnector connector() {
            return new InMemoryConnector(broker, InMemoryConnector.Behavior.DROP_ON_FAILURE);
        }

        @Override
        protected SourceConfig sourceConfig() {
            return config();
        }

        @Override
        protected ContractPeer peer() {
            return broker;
        }

        @Override
        protected java.time.Duration timeout() {
            return java.time.Duration.ofSeconds(3);
        }
    }

    static SourceConfig config() {
        return new SourceConfig(1, 3, SourceTypes.CONNECTOR, "in-memory", JsonMapper.builder().build().readTree("{}"), null, null);
    }

    @Test
    @DisplayName("DSC-09.02 AT-DSC-18.1 TC-DSC-235 기록 전에 확인하는 커넥터는 '기록 전 확인 금지' 시나리오에서 실패한다")
    void eagerAckConnectorFails() {
        Events tests = EngineTestKit.engine("junit-jupiter").selectors(selectClass(EagerAckRun.class)).execute().testEvents();
        tests.assertThatEvents().haveExactly(1, event(test("acknowledgesOnlyAfterWrite"), finishedWithFailure()))
                .haveExactly(1, event(test("deliversEveryMessageAsRawEnvelope"), finishedSuccessfully()))
                .haveExactly(1, event(test("descriptorSchemaAndCatalogEntryAreComplete"), finishedSuccessfully()));
    }

    @Test
    @DisplayName("DSC-09.03 기록 실패를 확인해 버려 메시지를 잃는 커넥터는 재전송 시나리오에서 실패한다")
    void droppingConnectorFails() {
        Events tests = EngineTestKit.engine("junit-jupiter").selectors(selectClass(DropOnFailureRun.class)).execute().testEvents();
        tests.assertThatEvents().haveExactly(1,
                event(test("failedWritesAreNotAcknowledgedAndAreRedelivered"), finishedWithFailure()));
    }

    @Test
    @DisplayName("기록 창구는 보류·실패·해제를 흉내 낸다")
    void recordingSink() {
        RecordingRawSink sink = new RecordingRawSink();
        RawEnvelope e = RawEnvelope.of(1, 3, SourceTypes.WEBHOOK, null, new byte[]{1}, Instant.EPOCH, "i", "k");
        sink.failNext(1);
        assertThat(sink.write(e).toCompletableFuture()).isCompletedExceptionally();
        sink.hold();
        var held = sink.write(e).toCompletableFuture();
        assertThat(held).isNotDone();
        assertThat(sink.heldCount()).isEqualTo(1);
        sink.release();
        assertThat(held).isCompleted();
        assertThat(sink.write(e).toCompletableFuture()).isCompleted();
        assertThat(sink.writtenCount()).isEqualTo(2);
        assertThat(sink.attempts()).isEqualTo(3);
    }
}
