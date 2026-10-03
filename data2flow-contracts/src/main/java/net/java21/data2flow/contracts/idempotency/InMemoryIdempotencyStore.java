package net.java21.data2flow.contracts.idempotency;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 메모리 멱등 저장소. 컨트롤러 슬라이스 테스트와 단일 인스턴스 로컬 실행용이다. 여러 파드에서는 키를 공유하지 못하므로
 * 운영에서는 {@link JdbcIdempotencyStore}를 쓴다.
 */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private final Map<IdempotencyScope, Row> rows = new HashMap<>();

    @Override
    public synchronized IdempotencyClaim claim(IdempotencyScope scope, String requestHash, Instant now,
                                               Instant expiresAt, Instant staleBefore) {
        Row row = rows.get(scope);
        if (row == null || !row.expiresAt().isAfter(now)) {
            rows.put(scope, new Row(requestHash, null, expiresAt, now));
            return IdempotencyClaim.acquired();
        }
        if (!row.requestHash().equals(requestHash)) {
            return IdempotencyClaim.mismatch();
        }
        if (row.response() != null) {
            return IdempotencyClaim.replay(row.response());
        }
        if (row.claimedAt().isBefore(staleBefore)) {
            rows.put(scope, new Row(requestHash, null, row.expiresAt(), now));
            return IdempotencyClaim.acquired();
        }
        return IdempotencyClaim.inProgress();
    }

    @Override
    public synchronized void complete(IdempotencyScope scope, StoredResponse response) {
        rows.computeIfPresent(scope, (k, row) -> new Row(row.requestHash(), response, row.expiresAt(), row.claimedAt()));
    }

    @Override
    public synchronized void release(IdempotencyScope scope) {
        Row row = rows.get(scope);
        if (row != null && row.response() == null) {
            rows.remove(scope);
        }
    }

    @Override
    public synchronized int deleteExpired(Instant now) {
        int before = rows.size();
        rows.values().removeIf(row -> !row.expiresAt().isAfter(now));
        return before - rows.size();
    }

    private record Row(String requestHash, StoredResponse response, Instant expiresAt, Instant claimedAt) {
    }
}
