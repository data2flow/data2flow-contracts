package net.java21.data2flow.contracts.messaging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Super Stream 파티션 라우팅 키(architecture.md §4.2, reliability-and-ha.md §2.1·§2.2).
 *
 * <p>Super Stream 생산자는 이 키를 해시해 파티션을 고른다(RabbitMQ Stream 클라이언트 기본 {@code HashRoutingStrategy}).
 * 키가 같으면 언제나 같은 파티션이고, 한 파티션은 활성 소비자 하나가 순서대로 처리하므로 기기별 순서가 지켜진다.
 */
public final class StreamRoutingKeys {

    private StreamRoutingKeys() {
    }

    /**
     * {@code data2flow.raw}: {@code sha1(sourceId + topic)}의 16진수(EVT-ING-01). ChirpStack은 토픽에 devEui가 있어 기기 단위 순서가 지켜지고,
     * 이중 ingress가 같은 메시지를 각각 기록해도 같은 파티션에 들어가 파티션 캐시로 중복을 거른다.
     *
     * @param topic MQTT 토픽 또는 Webhook 경로. 없으면 null(빈 문자열로 본다)
     */
    public static String raw(long sourceId, String topic) {
        return sha1Hex(sourceId + (topic == null ? "" : topic));
    }

    /** {@code data2flow.telemetry}: 기기 ID 10진수 문자열(EVT-ING-02). 같은 기기는 같은 파티션 */
    public static String telemetry(long deviceId) {
        return Long.toString(deviceId);
    }

    private static String sha1Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1을 쓸 수 없습니다", e);
        }
    }
}
