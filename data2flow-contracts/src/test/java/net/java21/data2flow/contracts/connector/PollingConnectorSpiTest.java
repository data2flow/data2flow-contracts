package net.java21.data2flow.contracts.connector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** DSC-09.09·09.10: 폴링 커서(BR-DSC-24)·리더 리스와 fencing(BR-DSC-26)·확장 방식(BR-DSC-25)·폴링 규칙(BR-DSC-17) */
class PollingConnectorSpiTest {

    private static final Instant T = Instant.parse("2026-10-03T00:00:00Z");

    @Test
    @DisplayName("DSC-09.09 TC-DSC-290 폴링 위치: 처음·증분 기준·다음 페이지, 1024자 제한")
    void pollCursor() {
        assertThat(PollCursor.initial().isInitial()).isTrue();
        PollCursor c = PollCursor.at("2026-10-03T00:00:00Z").nextPage("p2");
        assertThat(c.cursor()).isEqualTo("2026-10-03T00:00:00Z");
        assertThat(c.pageToken()).isEqualTo("p2");
        assertThat(c.isInitial()).isFalse();
        assertThat(new PollCursor(null, "p").isInitial()).isFalse();
        assertThatThrownBy(() -> PollCursor.at("x".repeat(1025))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PollCursor.initial().nextPage("x".repeat(1025))).isInstanceOf(IllegalArgumentException.class);
        PollCursorStore none = PollCursorStore.none();
        none.save(1, c);
        assertThat(none.load(1)).isEmpty();
    }

    @Test
    @DisplayName("DSC-09.10 TC-DSC-295 BR-DSC-26 리스 30초·넘겨받을 때마다 fencing token 증가, 낮은 토큰의 기록은 거부")
    void leaseAndFencing() {
        ConnectorLease first = ConnectorLease.acquire(3, "ingress-0", T, null);
        assertThat(first.fencingToken()).isEqualTo(1);
        assertThat(first.heldBy("ingress-0", T.plusSeconds(29))).isTrue();
        assertThat(first.validAt(T.plusSeconds(30))).isFalse();
        ConnectorLease renewed = first.renew(T.plusSeconds(10));
        assertThat(renewed.expiresAt()).isEqualTo(T.plusSeconds(40));
        assertThat(renewed.fencingToken()).isEqualTo(1);
        ConnectorLease taken = ConnectorLease.acquire(3, "ingress-1", T.plusSeconds(60), first);
        assertThat(taken.fencingToken()).isEqualTo(2);
        assertThat(taken.heldBy("ingress-0", T.plusSeconds(61))).isFalse();
        assertThat(taken.accepts(1)).isFalse();
        assertThat(taken.accepts(2)).isTrue();
        LeaseLostException lost = new LeaseLostException(3, 1);
        assertThat(lost.sourceId()).isEqualTo(3);
        assertThat(lost.fencingToken()).isEqualTo(1);
        assertThat(lost.getMessage()).contains("3");
        assertThatThrownBy(() -> new ConnectorLease(3, " ", T, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(ConnectorLease.RENEW_EVERY).isLessThan(ConnectorLease.TTL);
    }

    @Test
    @DisplayName("DSC-09.10 TC-DSC-292 BR-DSC-25 MQTT 5 + 공유 구독만 SCALABLE, 그 밖은 DUAL_ACTIVE")
    void scalingForMqtt() {
        assertThat(ScalingMode.forMqtt("5.0", true)).isEqualTo(ScalingMode.SCALABLE);
        assertThat(ScalingMode.forMqtt("5", true)).isEqualTo(ScalingMode.SCALABLE);
        assertThat(ScalingMode.forMqtt("5.0", false)).isEqualTo(ScalingMode.DUAL_ACTIVE);
        assertThat(ScalingMode.forMqtt("3.1.1", true)).isEqualTo(ScalingMode.DUAL_ACTIVE);
        assertThat(ScalingMode.forMqtt(null, true)).isEqualTo(ScalingMode.DUAL_ACTIVE);
    }

    @Test
    @DisplayName("DSC-09.09·06.05 BR-DSC-17 폴링 주기 10초 이상, 재시도 30초·2분·10분, 일일 한도 80% 경고·100% 정지")
    void pollingPolicy() {
        PollingPolicy p = new PollingPolicy(Duration.ofMinutes(60), 1000, 10_000);
        assertThat(p.quotaState(7_999)).isEqualTo(PollingPolicy.QuotaState.OK);
        assertThat(p.quotaState(8_000)).isEqualTo(PollingPolicy.QuotaState.WARN);
        assertThat(p.quotaState(10_000)).isEqualTo(PollingPolicy.QuotaState.EXHAUSTED);
        assertThat(new PollingPolicy(Duration.ofSeconds(10), 1, 0).quotaState(1_000_000)).isEqualTo(PollingPolicy.QuotaState.OK);
        assertThat(PollingPolicy.retryDelay(1)).contains(Duration.ofSeconds(30));
        assertThat(PollingPolicy.retryDelay(3)).contains(Duration.ofMinutes(10));
        assertThat(PollingPolicy.retryDelay(4)).isEmpty();
        assertThat(PollingPolicy.retryDelay(0)).isEmpty();
        assertThatThrownBy(() -> new PollingPolicy(Duration.ofSeconds(9), 1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PollingPolicy(null, 1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PollingPolicy(Duration.ofSeconds(10), 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DSC-09.02 실행 환경: 커서 저장소를 주지 않으면(M2 생성자) 저장하지 않는 저장소를 쓴다")
    void contextCursorStore() {
        ConnectorContext m2 = new ConnectorContext("ingress-0", Clock.fixed(T, ZoneOffset.UTC), null);
        assertThat(m2.cursorStore()).isSameAs(PollCursorStore.none());
        PollCursorStore custom = new PollCursorStore() {
            @Override
            public java.util.Optional<PollCursor> load(long sourceId) {
                return java.util.Optional.of(PollCursor.at("c"));
            }

            @Override
            public void save(long sourceId, PollCursor cursor) {
            }
        };
        assertThat(new ConnectorContext("ingress-0", Clock.systemUTC(), null, custom).cursorStore().load(1)).isPresent();
    }
}
