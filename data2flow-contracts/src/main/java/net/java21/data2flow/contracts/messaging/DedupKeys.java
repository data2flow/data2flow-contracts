package net.java21.data2flow.contracts.messaging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 원본 중복 판정 키 {@code RawEnvelope.dedupKey}(BR-ING-07, ING domain-model {@code raw_messages.dedup_key}, reliability-and-ha.md §2.1).
 *
 * <p>이중 ingress가 같은 원본을 각각 기록해도 키가 같아야 pipeline이 하나만 남긴다. 그래서 키는 수신 인스턴스·수신 순서와 무관한
 * 내용만으로 만든다. 모든 키는 128자 이하이다.
 * <ul>
 *   <li>ChirpStack 업링크: {@code chirpstack:{deduplicationId}}</li>
 *   <li>그 밖: {@code sha256:{hex(sha256(sourceId, topic, payload))}}</li>
 *   <li>측정 시각·카운터가 없는 값만 있는 payload: 수신 시각 버킷(기기 보고 주기의 절반)을 더한 {@code sha256b:…}.
 *       버킷 폭은 기기를 아는 쪽이 정한다</li>
 * </ul>
 */
public final class DedupKeys {

    /** ChirpStack v4 업링크 이벤트의 최상위 deduplicationId(UUID) */
    private static final Pattern CHIRPSTACK_DEDUP_ID =
            Pattern.compile("\"deduplicationId\"\\s*:\\s*\"([0-9a-fA-F-]{8,64})\"");
    private static final int SNIFF_LIMIT = 4096;

    private DedupKeys() {
    }

    public static String chirpStack(String deduplicationId) {
        if (deduplicationId == null || deduplicationId.isBlank()) {
            throw new IllegalArgumentException("deduplicationId가 비어 있습니다");
        }
        return "chirpstack:" + deduplicationId.toLowerCase();
    }

    public static String content(long sourceId, String topic, byte[] payload) {
        return "sha256:" + sha256(sourceId, topic, payload, null);
    }

    /**
     * 값만 있는 payload용: 같은 값이 보고 주기마다 오면 모두 저장하고, 반 주기 안에 다시 온 재전송만 중복으로 본다(BR-ING-07).
     *
     * @param bucketWidth 기기 보고 주기의 절반. 0보다 커야 한다
     */
    public static String contentInBucket(long sourceId, String topic, byte[] payload, Instant receivedAt,
                                         Duration bucketWidth) {
        if (bucketWidth.isZero() || bucketWidth.isNegative()) {
            throw new IllegalArgumentException("bucketWidth는 0보다 커야 합니다");
        }
        long bucket = Math.floorDiv(receivedAt.toEpochMilli(), bucketWidth.toMillis());
        return "sha256b:" + sha256(sourceId, topic, payload, bucket);
    }

    /**
     * 커넥터 기본값: payload 앞부분에 ChirpStack {@code deduplicationId}가 있으면 그것, 없으면 내용 해시.
     */
    public static String detect(long sourceId, String topic, byte[] payload) {
        int length = Math.min(payload.length, SNIFF_LIMIT);
        Matcher m = CHIRPSTACK_DEDUP_ID.matcher(new String(payload, 0, length, StandardCharsets.UTF_8));
        return m.find() ? chirpStack(m.group(1)) : content(sourceId, topic, payload);
    }

    private static String sha256(long sourceId, String topic, byte[] payload, Long bucket) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Long.toString(sourceId).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update((topic == null ? "" : topic).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(payload);
            if (bucket != null) {
                digest.update((byte) 0);
                digest.update(Long.toString(bucket).getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다", e);
        }
    }
}
