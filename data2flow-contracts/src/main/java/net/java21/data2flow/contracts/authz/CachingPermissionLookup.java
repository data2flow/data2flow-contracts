package net.java21.data2flow.contracts.authz;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 짧은 메모리 캐시를 둔 {@link PermissionLookup}. 역할·공간 권한 변경은 10초 안에 반영돼야 하므로(IAM-01.07, BR-IAM-13)
 * 수명은 10초를 넘을 수 없다. 파드마다 따로 캐시하고, 실패한 조회는 캐시하지 않는다.
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
        Key key = new Key(organizationId, userId);
        Instant now = clock.instant();
        Entry entry = cache.get(key);
        if (entry != null && now.isBefore(entry.expiresAt())) {
            return entry.grant();
        }
        AccessGrant grant = delegate.find(organizationId, userId);
        if (cache.size() >= MAX_ENTRIES) {
            cache.clear();
        }
        cache.put(key, new Entry(grant, now.plus(ttl)));
        return grant;
    }

    /** 권한 변경 이벤트를 받으면 바로 지운다 */
    public void evict(long organizationId, long userId) {
        cache.remove(new Key(organizationId, userId));
    }

    private record Key(long organizationId, long userId) {
    }

    private record Entry(AccessGrant grant, Instant expiresAt) {
    }
}
