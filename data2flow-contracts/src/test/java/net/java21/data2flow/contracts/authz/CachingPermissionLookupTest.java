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

    // ───────────── IAM-05.01·IAM-04.07: 웹 신원과 장기 토큰 신원을 같은 캐시 칸에 섞지 않는다 ─────────────

    private static final long ORG = 1;
    private static final long USER = 7;
    private static final long TOKEN = 501;
    private static final long FLOOR_2 = 20;

    /** core access-grant 흉내: 웹 신원은 ADMIN·전체 공간, 토큰 주체는 범위가 좁은 권한(소유자 권한 ∩ 토큰 범위·공간) */
    private static final PermissionLookup CORE = PermissionLookup.tokenAware((org, user, tokenId) -> tokenId == null
            ? AccessGrant.of(BuiltinRole.ADMIN, SpaceScope.all())
            : AccessGrant.custom(java.util.Set.of(Permission.SRC_READ), SpaceScope.only(java.util.Set.of(FLOOR_2))));

    private CachingPermissionLookup cached(AtomicInteger calls) {
        return new CachingPermissionLookup(PermissionLookup.tokenAware((org, user, tokenId) -> {
            calls.incrementAndGet();
            return CORE.find(org, user, tokenId);
        }), CachingPermissionLookup.MAX_TTL, MutableClock.atUtc("2026-10-06T00:00:00Z"));
    }

    @Test
    @DisplayName("[IAM-05.01][IAM-04.07] 같은 사용자 웹 → 토큰 순서: 좁은 토큰이 캐시된 넓은 웹 권한(ADMIN)을 물려받지 않는다")
    void webThenTokenDoesNotLeak() {
        // given
        AtomicInteger calls = new AtomicInteger();
        CachingPermissionLookup lookup = cached(calls);
        assertThat(lookup.find(ORG, USER).role()).isEqualTo("ADMIN");
        // when
        AccessGrant token = lookup.find(ORG, USER, TOKEN);
        // then
        assertThat(token.role()).isEqualTo("CUSTOM");
        assertThat(token.has(Permission.DEVICE_CONTROL)).isFalse();
        assertThat(token.spaceScope().includes(30L)).isFalse();
        assertThat(calls).hasValue(2);
    }

    @Test
    @DisplayName("[IAM-05.01][IAM-04.07] 같은 사용자 토큰 → 웹 순서: 웹 요청이 캐시된 좁은 토큰 권한으로 판정되지 않는다")
    void tokenThenWebDoesNotLeak() {
        // given
        AtomicInteger calls = new AtomicInteger();
        CachingPermissionLookup lookup = cached(calls);
        assertThat(lookup.find(ORG, USER, TOKEN).has(Permission.DEVICE_CONTROL)).isFalse();
        // when
        AccessGrant web = lookup.find(ORG, USER, null);
        // then
        assertThat(web.role()).isEqualTo("ADMIN");
        assertThat(web.spaceScope().unrestricted()).isTrue();
        // 각자 칸에서 다시 쓰이고(원천 2회), 토큰이 다르면 칸도 다르다
        assertThat(lookup.find(ORG, USER, TOKEN).role()).isEqualTo("CUSTOM");
        assertThat(lookup.find(ORG, USER).role()).isEqualTo("ADMIN");
        assertThat(calls).hasValue(2);
        lookup.find(ORG, USER, TOKEN + 1);
        assertThat(calls).hasValue(3);
    }

    @Test
    @DisplayName("[IAM-05.01][IAM-01.07] evict(조직, 사용자)는 그 사용자의 웹·토큰 칸을 모두 지운다")
    void evictRemovesWebAndTokenEntries() {
        AtomicInteger calls = new AtomicInteger();
        CachingPermissionLookup lookup = cached(calls);
        lookup.find(ORG, USER);
        lookup.find(ORG, USER, TOKEN);
        lookup.evict(ORG, USER);
        lookup.find(ORG, USER);
        lookup.find(ORG, USER, TOKEN);
        assertThat(calls).hasValue(4);
    }

    @Test
    @DisplayName("[IAM-05.01] 토큰을 모르는 이전 원천(2인자 람다)도 그대로 쓸 수 있고, 토큰 칸은 따로 둔다(이전 호환)")
    void legacyDelegateStillWorks() {
        AtomicInteger calls = new AtomicInteger();
        CachingPermissionLookup lookup = new CachingPermissionLookup((o, u) -> {
            calls.incrementAndGet();
            return AccessGrant.of(BuiltinRole.OPERATOR, SpaceScope.all());
        }, CachingPermissionLookup.MAX_TTL, MutableClock.atUtc("2026-10-06T00:00:00Z"));
        assertThat(lookup.find(ORG, USER).role()).isEqualTo("OPERATOR");
        assertThat(lookup.find(ORG, USER, TOKEN).role()).isEqualTo("OPERATOR");
        assertThat(calls).hasValue(2);
    }
}
