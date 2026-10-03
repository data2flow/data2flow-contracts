package net.java21.data2flow.contracts.idempotency;

import java.time.Instant;

/**
 * 멱등 키 저장소 SPI(BR-OPS-20). 여러 파드가 함께 보도록 DB에 둔다. PostgreSQL 구현은 {@link JdbcIdempotencyStore},
 * 테스트·단일 인스턴스용은 {@link InMemoryIdempotencyStore}다.
 *
 * <p>구현은 원자적이어야 한다: 같은 범위로 동시에 {@code claim}하면 하나만 ACQUIRED를 받는다.
 */
public interface IdempotencyStore {

    /**
     * 키를 잡는다.
     *
     * @param scope       키 범위
     * @param requestHash 요청 SHA-256(hex 64자)
     * @param now         지금
     * @param expiresAt   새로 잡으면 보관할 시각(지금 + 24시간)
     * @param staleBefore 이 시각보다 먼저 잡혔는데 아직 결과가 없으면 처리 중 파드가 죽은 것으로 보고 넘겨받는다
     */
    IdempotencyClaim claim(IdempotencyScope scope, String requestHash, Instant now, Instant expiresAt, Instant staleBefore);

    /** 처리가 끝난 응답을 저장한다 */
    void complete(IdempotencyScope scope, StoredResponse response);

    /** 처리에 실패해 키를 푼다(아직 결과가 없는 경우만) */
    void release(IdempotencyScope scope);

    /** 보관 기간이 지난 키를 지운다(서비스 스케줄러가 주기적으로 부른다). 지운 수 */
    int deleteExpired(Instant now);
}
