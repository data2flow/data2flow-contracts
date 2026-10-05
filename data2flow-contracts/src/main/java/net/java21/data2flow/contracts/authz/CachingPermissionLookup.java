package net.java21.data2flow.contracts.authz;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 짧은 메모리 캐시를 둔 {@link PermissionLookup}. 역할·공간 권한 변경은 10초 안에 반영돼야 하므로(IAM-01.07, BR-IAM-13)
 * 수명은 10초를 넘을 수 없다. 파드마다 따로 캐시하고, 실패한 조회는 캐시하지 않는다.
 *
 * <p>캐시 키는 조직·사용자·<b>토큰 ID</b>(웹 신원이면 웹 구분값)다(IAM-05.01·IAM-04.07). 같은 사용자라도 웹 세션과 장기 토큰의 권한은
 * 다르므로(토큰은 범위·공간이 좁다) 한 칸에 섞으면 좁은 토큰이 넓은 웹 권한을 물려받거나 반대가 된다.
 */
public class CachingPermissionLookup implements PermissionLookup {

    public static final Duration MAX_TTL = Duration.ofSeconds(10);
    private static final int MAX_ENTRIES = 10_000;

    private final PermissionLookup delegate;
    private final Duration ttl;
    private final Clock clock;
    private final Map<Key, Entry> cache = new ConcurrentHashMap<>();

    public CachingPermissionLookup(PermissionLookup delegate, Duration ttl, Clock clock) {
        if (ttl.isNegative() || ttl.compareTo(MAX_TTL) > 0) {
            throw new IllegalArgumentException("권한 캐시 수명은 0~10초여야 합니다(BR-IAM-13): " + ttl);
        }
        this.delegate = delegate;
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public AccessGrant find(long organizationId, long userId) {
        return find(organizationId, userId, null);
    }

    @Override
    public AccessGrant find(long organizationId, long userId, Long accessTokenId) {
        Key key = new Key(organizationId, userId, accessTokenId == null ? WEB : accessTokenId);
        Instant now = clock.instant();
        Entry entry = cache.get(key);
        if (entry != null && now.isBefore(entry.expiresAt())) {
            return entry.grant();
        }
        AccessGrant grant = delegate.find(organizationId, userId, accessTokenId);
        if (cache.size() >= MAX_ENTRIES) {
            cache.clear();
        }
        cache.put(key, new Entry(grant, now.plus(ttl)));
        return grant;
    }

    /** 권한 변경 이벤트를 받으면 바로 지운다. 그 사용자의 웹 칸과 모든 토큰 칸을 함께 지운다 */
    public void evict(long organizationId, long userId) {
        cache.keySet().removeIf(k -> k.organizationId() == organizationId && k.userId() == userId);
    }

    /** 웹 신원 칸의 구분값. 토큰 ID는 IDENTITY(1부터)라 겹치지 않는다 */
    private static final long WEB = Long.MIN_VALUE;

    /** @param principal 토큰 ID, 웹 신원이면 {@link #WEB} */
    private record Key(long organizationId, long userId, long principal) {
    }

    private record Entry(AccessGrant grant, Instant expiresAt) {
    }
}
