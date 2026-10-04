package net.java21.data2flow.contracts.connector;

import java.time.Duration;
import java.time.Instant;

/**
 * SINGLETON 커넥터 리더 리스(DSC domain-model §6.6 {@code connector_leases}, DSC-09.10, BR-DSC-26). 30초 리스를 10초마다 갱신하고,
 * 리스를 잃으면 즉시 수집을 멈춘다. 넘겨받을 때마다 fencing token이 커진다.
 *
 * @param sourceId       소스
 * @param holderInstance 리스를 가진 ingress 인스턴스
 * @param expiresAt      만료 시각
 * @param fencingToken   넘겨받을 때마다 1씩 커지는 번호
 */
public record ConnectorLease(long sourceId, String holderInstance, Instant expiresAt, long fencingToken) {

    /** 리스 길이 */
    public static final Duration TTL = Duration.ofSeconds(30);
    /** 갱신 주기 */
    public static final Duration RENEW_EVERY = Duration.ofSeconds(10);

    public ConnectorLease {
        if (sourceId < 1 || holderInstance == null || holderInstance.isBlank() || expiresAt == null || fencingToken < 1) {
            throw new IllegalArgumentException("sourceId·holderInstance·expiresAt·fencingToken은 필수입니다");
        }
    }

    /** 처음 얻은 리스 */
    public static ConnectorLease acquire(long sourceId, String instance, Instant now, ConnectorLease previous) {
        long token = previous == null ? 1 : previous.fencingToken + 1;
        return new ConnectorLease(sourceId, instance, now.plus(TTL), token);
    }

    /** 갱신(같은 토큰) */
    public ConnectorLease renew(Instant now) {
        return new ConnectorLease(sourceId, holderInstance, now.plus(TTL), fencingToken);
    }

    /** 이 시각에 유효한가 */
    public boolean validAt(Instant now) {
        return now.isBefore(expiresAt);
    }

    /** {@code instance}가 지금 이 리스를 가졌는가 */
    public boolean heldBy(String instance, Instant now) {
        return holderInstance.equals(instance) && validAt(now);
    }

    /** 이 토큰으로 쓴 기록을 받아도 되는가(낮은 토큰은 거부) */
    public boolean accepts(long writerToken) {
        return writerToken >= fencingToken;
    }
}
