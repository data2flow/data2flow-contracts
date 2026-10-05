package net.java21.data2flow.contracts.message;

import net.java21.data2flow.contracts.messaging.StreamRoutingKeys;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * @param signatureStatus 플랫폼 브로커 기기 payload 서명 검증 결과({@link SignatureStatus}: VERIFIED·UNSIGNED·INVALID,
 *                        DSC-03.03·03.05, ADR-042). ingress가 PLATFORM_BROKER 소스에만 채우고, 그 밖에는 null(JSON에서 생략).
 *                        선택 필드라 v는 1 그대로이고, 이 필드를 모르는 소비자는 무시한다
 * @param payloadFormat   ingress가 {@code payload}를 구조화된 JSON으로 바꿨을 때 원래 형식({@code PayloadFormat} 이름: CBOR·MSGPACK·
 *                        PROTOBUF·AVRO·CSV·SPARKPLUG_B, 압축만 풀었으면 JSON·TEXT·BINARY, DSC-09.07). 바꾸지 않았으면 null
 * @param originalPayload {@code payloadFormat}이 있을 때 받은 그대로의 바이트(무손실 보관, 재처리·감사용). 아니면 null
 * @param topicAttributes 토픽 템플릿(DSC-09.08)으로 토픽·경로에서 뽑은 값(변수 이름 → 값). 예약 키 {@link IngressStatus#ATTR_EXTERNAL_ID}·
 *                        {@link IngressStatus#ATTR_METRIC}·{@link IngressStatus#ATTR_SPACE_HINT}. 템플릿이 없으면 null
 * @param ingressStatus   ingress가 판정한 미처리 상태({@link IngressStatus}: DECODE_ERROR·UNMATCHED_TOPIC). 이때 {@code payload}는 받은
 *                        그대로이고 pipeline은 디코딩하지 않고 원본만 그 상태로 남긴다(BR-DSC-28). 정상이면 null
 * @param ingressError    {@code ingressStatus}의 사람이 읽는 원인(500자 이하). 없으면 null
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
        Long simRunId,
        String signatureStatus,
        String payloadFormat,
        byte[] originalPayload,
        Map<String, String> topicAttributes,
        String ingressStatus,
        String ingressError) implements Message {

    public static final int VERSION = 1;
    public static final int MAX_DEDUP_KEY_LENGTH = 128;
    public static final int MAX_INGRESS_ERROR_LENGTH = 500;

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
        topicAttributes = topicAttributes == null ? null : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(topicAttributes));
        if (ingressError != null && ingressError.length() > MAX_INGRESS_ERROR_LENGTH) {
            ingressError = ingressError.substring(0, MAX_INGRESS_ERROR_LENGTH);
        }
    }

    /** 형식 변환·토픽 템플릿 결과가 없는 원본 봉투(13개 필드 생성자, 하위 호환) */
    public RawEnvelope(int v, UUID messageId, long organizationId, long sourceId, String sourceType, String topic, byte[] payload,
                       Instant receivedAt, String ingressInstance, String dedupKey, boolean virtual, Long simRunId,
                       String signatureStatus) {
        this(v, messageId, organizationId, sourceId, sourceType, topic, payload, receivedAt, ingressInstance, dedupKey, virtual,
                simRunId, signatureStatus, null, null, null, null, null);
    }

    /** 서명 결과가 없는 원본 봉투(기존 12개 필드 생성자, 하위 호환) */
    public RawEnvelope(int v, UUID messageId, long organizationId, long sourceId, String sourceType, String topic, byte[] payload,
                       Instant receivedAt, String ingressInstance, String dedupKey, boolean virtual, Long simRunId) {
        this(v, messageId, organizationId, sourceId, sourceType, topic, payload, receivedAt, ingressInstance, dedupKey, virtual,
                simRunId, null);
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
                ingressInstance, dedupKey, true, runId, signatureStatus, payloadFormat, originalPayload, topicAttributes,
                ingressStatus, ingressError);
    }

    /**
     * 서명 검증 결과를 붙인 사본(ingress, DSC-03.03). 서명을 떼어 낸 본문으로 바꿀 때는 {@code payload}도 함께 준다.
     *
     * @param status  {@link SignatureStatus} 값
     * @param payload 기록할 바이트(서명 접두사를 뗀 본문 또는 받은 그대로)
     */
    public RawEnvelope withSignature(String status, byte[] payload) {
        return new RawEnvelope(v, messageId, organizationId, sourceId, sourceType, topic, payload, receivedAt,
                ingressInstance, dedupKey, virtual, simRunId, status, payloadFormat, originalPayload, topicAttributes,
                ingressStatus, ingressError);
    }

    /**
     * ingress가 payload를 구조화된 JSON으로 바꾼 사본(DSC-09.07). 받은 바이트는 {@code originalPayload}로 옮겨 보관한다.
     *
     * @param format    원래 형식({@code PayloadFormat} 이름)
     * @param converted 바꾼 JSON(UTF-8)
     * @param key       중복 판정 키(바꾼 내용에서 더 나은 키를 찾았으면 그것, 아니면 지금 키)
     */
    public RawEnvelope withConvertedPayload(String format, byte[] converted, String key) {
        return new RawEnvelope(v, messageId, organizationId, sourceId, sourceType, topic, converted, receivedAt,
                ingressInstance, key, virtual, simRunId, signatureStatus, format,
                originalPayload != null ? originalPayload : payload, topicAttributes, ingressStatus, ingressError);
    }

    /** 토픽 템플릿으로 뽑은 값을 붙인 사본(DSC-09.08) */
    public RawEnvelope withTopicAttributes(Map<String, String> attributes) {
        return new RawEnvelope(v, messageId, organizationId, sourceId, sourceType, topic, payload, receivedAt,
                ingressInstance, dedupKey, virtual, simRunId, signatureStatus, payloadFormat, originalPayload, attributes,
                ingressStatus, ingressError);
    }

    /** ingress 미처리 판정을 붙인 사본(BR-DSC-28). payload는 그대로다 */
    public RawEnvelope withIngressStatus(String status, String error) {
        return new RawEnvelope(v, messageId, organizationId, sourceId, sourceType, topic, payload, receivedAt,
                ingressInstance, dedupKey, virtual, simRunId, signatureStatus, payloadFormat, originalPayload, topicAttributes,
                status, error);
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
                && Objects.equals(simRunId, other.simRunId) && Objects.equals(signatureStatus, other.signatureStatus)
                && Objects.equals(payloadFormat, other.payloadFormat) && Arrays.equals(originalPayload, other.originalPayload)
                && Objects.equals(topicAttributes, other.topicAttributes) && Objects.equals(ingressStatus, other.ingressStatus)
                && Objects.equals(ingressError, other.ingressError);
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
                + ", virtual=" + virtual + ", simRunId=" + simRunId + ", signatureStatus=" + signatureStatus
                + ", payloadFormat=" + payloadFormat
                + ", originalPayload=" + (originalPayload == null ? "null" : originalPayload.length + " bytes")
                + ", topicAttributes=" + (topicAttributes == null ? "null" : topicAttributes.keySet())
                + ", ingressStatus=" + ingressStatus + "]";
    }
}
