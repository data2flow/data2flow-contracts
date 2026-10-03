package net.java21.data2flow.contracts.message;

import net.java21.data2flow.contracts.messaging.StreamRoutingKeys;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * 원본 수신 메시지 {@code RawEnvelope} v1(EVT-ING-01, ING-01.01). Super Stream {@code data2flow.raw}의 본문이다.
 *
 * <p>ingress(커넥터)와 simulator가 만들고 pipeline이 읽는다. {@code payload}는 받은 바이트 그대로이며 JSON에서는 base64 문자열이다.
 * 파티션 라우팅 키는 {@link #routingKey()}(소스 + 토픽 해시)라서 같은 기기의 메시지는 같은 파티션에서 순서대로 처리된다.
 *
 * @param v               스키마 버전(1)
 * @param messageId       메시지 ID(UUID). 이중 ingress가 같은 원본을 각각 기록하면 messageId는 다르고 {@code dedupKey}는 같다
 * @param organizationId  조직 ID
 * @param sourceId        데이터 소스 ID
 * @param sourceType      DSC {@code data_sources.type}과 같은 값({@link SourceTypes})
 * @param topic           MQTT 토픽 또는 Webhook 경로. 없으면 null
 * @param payload         원본 바이트
 * @param receivedAt      ingress 수신 시각(UTC)
 * @param ingressInstance 받은 ingress 파드 이름
 * @param dedupKey        중복 판정 키(BR-ING-07, {@link net.java21.data2flow.contracts.messaging.DedupKeys}), 128자 이하
 * @param virtual         가상 환경 메시지(SIM)이면 true
 * @param simRunId        가상 실행 ID(SIM). 없으면 null
 */
@MessageSchema(name = "raw-envelope", version = 1)
public record RawEnvelope(
        int v,
        UUID messageId,
        long organizationId,
        long sourceId,
        String sourceType,
        String topic,
        byte[] payload,
        Instant receivedAt,
        String ingressInstance,
        String dedupKey,
        boolean virtual,
        Long simRunId) implements Message {

    public static final int VERSION = 1;
    public static final int MAX_DEDUP_KEY_LENGTH = 128;

    public RawEnvelope {
        Messages.requireVersion(v);
        Messages.require(messageId, "messageId");
        Messages.requireId(organizationId, "organizationId");
        Messages.requireId(sourceId, "sourceId");
        Messages.requireText(sourceType, "sourceType");
        Messages.require(payload, "payload");
        Messages.require(receivedAt, "receivedAt");
        Messages.requireText(ingressInstance, "ingressInstance");
        Messages.requireText(dedupKey, "dedupKey");
        if (dedupKey.length() > MAX_DEDUP_KEY_LENGTH) {
            throw new MessageFormatException("dedupKey는 " + MAX_DEDUP_KEY_LENGTH + "자 이하여야 합니다");
        }
    }

    /** 현재 버전으로 새 원본 봉투를 만든다(messageId는 새 UUID) */
    public static RawEnvelope of(long organizationId, long sourceId, String sourceType, String topic, byte[] payload,
                                 Instant receivedAt, String ingressInstance, String dedupKey) {
        return new RawEnvelope(VERSION, UUID.randomUUID(), organizationId, sourceId, sourceType, topic, payload,
                receivedAt, ingressInstance, dedupKey, false, null);
    }

    /** 가상 환경(SIM) 표시를 붙인 사본 */
    public RawEnvelope asVirtual(Long runId) {
        return new RawEnvelope(v, messageId, organizationId, sourceId, sourceType, topic, payload, receivedAt,
                ingressInstance, dedupKey, true, runId);
    }

    /** {@code data2flow.raw} 파티션 라우팅 키 */
    public String routingKey() {
        return StreamRoutingKeys.raw(sourceId, topic);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RawEnvelope other
                && v == other.v && organizationId == other.organizationId && sourceId == other.sourceId
                && virtual == other.virtual && messageId.equals(other.messageId)
                && sourceType.equals(other.sourceType) && Objects.equals(topic, other.topic)
                && Arrays.equals(payload, other.payload) && receivedAt.equals(other.receivedAt)
                && ingressInstance.equals(other.ingressInstance) && dedupKey.equals(other.dedupKey)
                && Objects.equals(simRunId, other.simRunId);
    }

    @Override
    public int hashCode() {
        return 31 * messageId.hashCode() + Arrays.hashCode(payload);
    }

    /** 원본 바이트는 로그에 남기지 않는다(비밀값이 섞일 수 있음, NFR-03.02) */
    @Override
    public String toString() {
        return "RawEnvelope[v=" + v + ", messageId=" + messageId + ", organizationId=" + organizationId
                + ", sourceId=" + sourceId + ", sourceType=" + sourceType + ", topic=" + topic
                + ", payload=" + payload.length + " bytes, receivedAt=" + receivedAt
                + ", ingressInstance=" + ingressInstance + ", dedupKey=" + dedupKey
                + ", virtual=" + virtual + ", simRunId=" + simRunId + "]";
    }
}
