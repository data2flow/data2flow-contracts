package net.java21.data2flow.contracts.message;

import net.java21.data2flow.contracts.messaging.DedupKeys;
import net.java21.data2flow.contracts.messaging.StreamRoutingKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ING-01.01: 원본 봉투 RawEnvelope v1 계약(EVT-ING-01, data2flow.raw) */
class RawEnvelopeContractTest {

    private final MessageCodec codec = MessageCodec.create();
    private static final byte[] PAYLOAD = "{\"deduplicationId\":\"0b5c\",\"object\":{\"temperature\":22.3}}"
            .getBytes(StandardCharsets.UTF_8);

    private RawEnvelope sample() {
        return RawEnvelope.of(1, 3, SourceTypes.MQTT_SUBSCRIBE, "application/1/device/24e124136d151606/event/up",
                PAYLOAD, Instant.parse("2026-10-03T02:40:09.501Z"), "data2flow-ingress-0", "chirpstack:0b5c");
    }

    @Test
    @DisplayName("ING-01.01 TC-ING-001 payload는 base64로 쓰고 같은 바이트로 읽으며 raw-envelope.v1.json을 통과한다")
    void roundTripsAndMatchesSchema() {
        RawEnvelope raw = sample();
        JsonNode tree = codec.toTree(raw);
        assertThat(tree.get("payload").asString()).isEqualTo(Base64.getEncoder().encodeToString(PAYLOAD));
        assertThat(tree.get("v").asInt()).isEqualTo(1);
        assertThat(tree.get("receivedAt").asString()).isEqualTo("2026-10-03T02:40:09.501Z");
        MessageSchemas.assertValid(raw);
        RawEnvelope read = codec.read(codec.write(raw), RawEnvelope.class);
        assertThat(read).isEqualTo(raw).hasSameHashCodeAs(raw);
        assertThat(read.payload()).isEqualTo(PAYLOAD);
    }

    @Test
    @DisplayName("ING-01.01 TC-ING-001 라우팅 키는 sha1(sourceId + topic)이라 이중 ingress의 같은 메시지는 같은 파티션")
    void routingKeyIsSourceAndTopicHash() {
        RawEnvelope a = sample();
        RawEnvelope b = RawEnvelope.of(1, 3, SourceTypes.MQTT_SUBSCRIBE, a.topic(), PAYLOAD, a.receivedAt().plusMillis(3),
                "data2flow-ingress-1", a.dedupKey());
        assertThat(a.routingKey()).isEqualTo(b.routingKey()).hasSize(40)
                .isEqualTo(StreamRoutingKeys.raw(3, "application/1/device/24e124136d151606/event/up"));
        assertThat(a.messageId()).isNotEqualTo(b.messageId());
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("ING-01.01 SIM 메시지는 virtual=true와 simRunId를 싣는다")
    void virtualCopy() {
        RawEnvelope original = sample();
        RawEnvelope sim = original.asVirtual(77L);
        assertThat(sim.virtual()).isTrue();
        assertThat(sim.simRunId()).isEqualTo(77L);
        assertThat(sim.messageId()).isEqualTo(original.messageId());
        MessageSchemas.assertValid(sim);
        assertThat(codec.read(codec.write(sim), RawEnvelope.class)).isEqualTo(sim);
    }

    @Test
    @DisplayName("NFR-03.02 toString은 원본 바이트를 남기지 않는다")
    void toStringHidesPayload() {
        assertThat(sample().toString()).contains("payload=" + PAYLOAD.length + " bytes").doesNotContain("temperature");
    }

    @Test
    @DisplayName("ING-01.01 필수 필드·dedupKey 128자 제한·모르는 버전을 검사한다")
    void validation() {
        assertThatThrownBy(() -> RawEnvelope.of(1, 3, SourceTypes.WEBHOOK, null, PAYLOAD, Instant.EPOCH, "i", "k".repeat(129)))
                .isInstanceOf(MessageFormatException.class).hasMessageContaining("128");
        assertThatThrownBy(() -> RawEnvelope.of(0, 3, SourceTypes.WEBHOOK, null, PAYLOAD, Instant.EPOCH, "i", "k"))
                .isInstanceOf(MessageFormatException.class).hasMessageContaining("organizationId");
        assertThatThrownBy(() -> new RawEnvelope(1, UUID.randomUUID(), 1, 3, SourceTypes.WEBHOOK, null, null,
                Instant.EPOCH, "i", "k", false, null)).isInstanceOf(MessageFormatException.class);
        assertThatThrownBy(() -> RawEnvelope.of(1, 3, " ", null, PAYLOAD, Instant.EPOCH, "i", "k"))
                .isInstanceOf(MessageFormatException.class);
        String v2 = codec.writeAsString(sample()).replace("\"v\":1", "\"v\":2");
        assertThatThrownBy(() -> codec.read(v2, RawEnvelope.class)).isInstanceOf(UnsupportedSchemaVersionException.class);
        assertThat(SourceTypes.KNOWN).contains(SourceTypes.CONNECTOR, SourceTypes.EDGE, SourceTypes.SIMULATION).hasSize(13);
    }

    @Test
    @DisplayName("DSC-09.07 TC-DSC-280 형식 변환 사본은 바꾼 JSON을 payload로, 받은 바이트를 originalPayload로 싣고 스키마를 통과한다")
    void convertedPayloadKeepsOriginal() {
        byte[] original = {0x09, 0, 0, 0, 0, 0, (byte) 0x80, 0x36, 0x40, 0x10, 0x07};
        RawEnvelope raw = RawEnvelope.of(1, 5, SourceTypes.CONNECTOR, "site/a/room/301/em-1/temperature", original,
                Instant.parse("2026-10-05T01:00:00Z"), "data2flow-ingress-0", DedupKeys.content(5, "t", original));
        byte[] json = "{\"temperature\":22.5,\"seq\":7}".getBytes(StandardCharsets.UTF_8);
        RawEnvelope converted = raw.withTopicAttributes(Map.of("deviceId", "em-1", IngressStatus.ATTR_EXTERNAL_ID, "em-1"))
                .withConvertedPayload("PROTOBUF", json, raw.dedupKey());
        assertThat(converted.payload()).isEqualTo(json);
        assertThat(converted.originalPayload()).isEqualTo(original);
        assertThat(converted.payloadFormat()).isEqualTo("PROTOBUF");
        assertThat(converted.withConvertedPayload("PROTOBUF", json, raw.dedupKey()).originalPayload())
                .as("두 번 바꿔도 원본은 처음 받은 바이트").isEqualTo(original);
        MessageSchemas.assertValid(converted);
        RawEnvelope read = codec.read(codec.write(converted), RawEnvelope.class);
        assertThat(read).isEqualTo(converted);
        assertThat(read.topicAttributes()).containsEntry("externalId", "em-1");
        assertThat(converted.toString()).contains("originalPayload=11 bytes").doesNotContain("em-1\"");
        assertThat(converted.asVirtual(3L).payloadFormat()).isEqualTo("PROTOBUF");
        assertThat(converted.withSignature(SignatureStatus.VERIFIED, json).originalPayload()).isEqualTo(original);
    }

    @Test
    @DisplayName("DSC-09.08 TC-DSC-285 BR-DSC-28 미처리 판정은 payload를 그대로 두고 상태·원인만 싣는다(원인은 500자에서 자른다)")
    void ingressStatusKeepsPayload() {
        RawEnvelope raw = sample().withIngressStatus(IngressStatus.UNMATCHED_TOPIC, "x".repeat(600));
        assertThat(raw.payload()).isEqualTo(PAYLOAD);
        assertThat(raw.ingressStatus()).isEqualTo("UNMATCHED_TOPIC");
        assertThat(raw.ingressError()).hasSize(RawEnvelope.MAX_INGRESS_ERROR_LENGTH);
        MessageSchemas.assertValid(raw);
        JsonNode tree = codec.toTree(sample());
        assertThat(tree.path("ingressStatus").isNull() || tree.path("ingressStatus").isMissingNode()).as("없으면 null").isTrue();
        assertThat(tree.path("originalPayload").isNull() || tree.path("originalPayload").isMissingNode()).isTrue();
    }
}
