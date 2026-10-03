package net.java21.data2flow.contracts.authz;

import net.java21.data2flow.contracts.support.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** IAM-01.07·BR-IAM-13: 역할 변경은 10초 안에 반영된다(권한 캐시 10초 이내) */
class CachingPermissionLookupTest {

    @Test
    @DisplayName("[IAM-01.07][AT-IAM-09.1] 역할을 VIEWER로 바꾸면 캐시 수명(10초)이 지난 다음 요청부터 새 권한")
    void roleChangeWithinTenSeconds() {
        // given
        MutableClock clock = MutableClock.atUtc("2026-10-03T00:00:00Z");
        AtomicReference<AccessGrant> source = new AtomicReference<>(AccessGrant.of(BuiltinRole.OPERATOR, SpaceScope.all()));
        AtomicInteger calls = new AtomicInteger();
        CachingPermissionLookup lookup = new CachingPermissionLookup((o, u) -> {
            calls.incrementAndGet();
            return source.get();
        }, CachingPermissionLookup.MAX_TTL, clock);
        assertThat(lookup.find(1, 7).role()).isEqualTo("OPERATOR");

        // when
        source.set(AccessGrant.of(BuiltinRole.VIEWER, SpaceScope.all()));
        clock.advance(Duration.ofSeconds(9));
        // then
        assertThat(lookup.find(1, 7).role()).isEqualTo("OPERATOR");
        clock.advance(Duration.ofSeconds(1));
        assertThat(lookup.find(1, 7).role()).isEqualTo("VIEWER");
        assertThat(calls).hasValue(2);

        source.set(AccessGrant.of(BuiltinRole.ADMIN, SpaceScope.all()));
        lookup.evict(1, 7);
        assertThat(lookup.find(1, 7).role()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("[BR-IAM-13] 캐시 수명은 10초를 넘을 수 없다")
    void ttlLimit() {
        MutableClock clock = MutableClock.atUtc("2026-10-03T00:00:00Z");
        assertThatThrownBy(() -> new CachingPermissionLookup((o, u) -> AccessGrant.none(), Duration.ofSeconds(11), clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CachingPermissionLookup((o, u) -> AccessGrant.none(), Duration.ofSeconds(-1), clock))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
