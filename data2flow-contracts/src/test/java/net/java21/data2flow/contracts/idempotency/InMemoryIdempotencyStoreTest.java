package net.java21.data2flow.contracts.idempotency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** OPS-12.03: 메모리 저장소의 판정(슬라이스 테스트 대역이 운영 구현과 같게 동작하는지) */
class InMemoryIdempotencyStoreTest {

    private static final Instant T0 = Instant.parse("2026-10-03T00:00:00Z");
    private static final Instant EXPIRES = T0.plusSeconds(86_400);
    private final IdempotencyScope scope = new IdempotencyScope(1, 7, "POST /core/jobs", "k");

    @Test
    @DisplayName("[OPS-12.03][BR-OPS-20] 잡기 → 처리 중 → 완료 후 재생, 해시가 다르면 불일치, 풀면 다시 잡는다")
    void lifecycle() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
        assertThat(store.claim(scope, "h", T0, EXPIRES, T0.minusSeconds(60)).outcome()).isEqualTo(IdempotencyClaim.Outcome.ACQUIRED);
        assertThat(store.claim(scope, "h", T0, EXPIRES, T0.minusSeconds(60)).outcome()).isEqualTo(IdempotencyClaim.Outcome.IN_PROGRESS);
        assertThat(store.claim(scope, "other", T0, EXPIRES, T0.minusSeconds(60)).outcome()).isEqualTo(IdempotencyClaim.Outcome.MISMATCH);

        StoredResponse response = new StoredResponse(202, "application/json", Map.of(), "{}".getBytes());
        store.complete(scope, response);
        store.release(scope); // 결과가 있으면 풀지 않는다
        IdempotencyClaim replay = store.claim(scope, "h", T0, EXPIRES, T0.minusSeconds(60));
        assertThat(replay.outcome()).isEqualTo(IdempotencyClaim.Outcome.REPLAY);
        assertThat(replay.response()).isEqualTo(response).hasSameHashCodeAs(response);
        assertThat(replay.response().toString()).contains("bodyLength=2");

        assertThat(store.deleteExpired(EXPIRES)).isEqualTo(1);
        IdempotencyScope other = new IdempotencyScope(1, 7, "POST /core/jobs", "k2");
        store.claim(other, "h", T0, EXPIRES, T0);
        store.release(other);
        assertThat(store.claim(other, "h", T0, EXPIRES, T0).outcome()).isEqualTo(IdempotencyClaim.Outcome.ACQUIRED);
    }
}
