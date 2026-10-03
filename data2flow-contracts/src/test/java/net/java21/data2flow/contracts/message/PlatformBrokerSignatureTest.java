package net.java21.data2flow.contracts.message;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** DSC-03.03·03.05(ADR-042): 플랫폼 브로커 기기 payload 서명 형식과 RawEnvelope.signatureStatus 하위 호환 */
class PlatformBrokerSignatureTest {

    private static final byte[] BODY = "{\"co2\":812,\"temperature\":23.4}".getBytes(StandardCharsets.UTF_8);
    private final MessageCodec codec = MessageCodec.create();

    @Test
    @DisplayName("DSC-03.03 TC-DSC-114 같은 키로 서명하면 검증 통과, 다른 기기의 키·바뀐 본문은 실패")
    void signAndVerify() {
        byte[] payload = PlatformBrokerSignature.sign("key-a", BODY);
        assertThat(new String(payload, StandardCharsets.US_ASCII)).startsWith("v1.").contains(".{\"co2\"");
        PlatformBrokerSignature.Signed signed = PlatformBrokerSignature.parse(payload).orElseThrow();
        assertThat(signed.body()).isEqualTo(BODY);
        assertThat(signed.signatureHex()).hasSize(64);
        assertThat(PlatformBrokerSignature.verify("key-a", signed)).isTrue();
        assertThat(PlatformBrokerSignature.verify("key-b", signed)).isFalse();
        byte[] tampered = payload.clone();
        tampered[tampered.length - 2] = '5';
        assertThat(PlatformBrokerSignature.verify("key-a", PlatformBrokerSignature.parse(tampered).orElseThrow())).isFalse();
    }

    @Test
    @DisplayName("DSC-03.05 서명 접두사가 없거나 형식이 틀리면 서명 없음으로 본다")
    void unsignedPayloads() {
        assertThat(PlatformBrokerSignature.parse(BODY)).isEmpty();
        assertThat(PlatformBrokerSignature.parse(null)).isEmpty();
        assertThat(PlatformBrokerSignature.parse("v1.zz".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        String badHex = "v1." + "g".repeat(64) + "." + "{}";
        assertThat(PlatformBrokerSignature.parse(badHex.getBytes(StandardCharsets.US_ASCII))).isEmpty();
        String noDot = "v1." + "a".repeat(64) + "x{}";
        assertThat(PlatformBrokerSignature.parse(noDot.getBytes(StandardCharsets.US_ASCII))).isEmpty();
        String v2 = "v2." + "a".repeat(64) + ".{}";
        assertThat(PlatformBrokerSignature.parse(v2.getBytes(StandardCharsets.US_ASCII))).isEmpty();
        String upper = "v1." + "A".repeat(64) + ".{}";
        assertThat(PlatformBrokerSignature.parse(upper.getBytes(StandardCharsets.US_ASCII)).orElseThrow().signatureHex())
                .isEqualTo("a".repeat(64));
        assertThat(PlatformBrokerSignature.verify("k", new PlatformBrokerSignature.Signed("xyz", BODY))).isFalse();
        assertThatThrownBy(() -> PlatformBrokerSignature.sign("", BODY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DSC-03.05 signatureStatus는 선택 필드: 없으면 JSON에서 읽어도 null, 있으면 왕복하고 스키마를 통과한다")
    void signatureStatusIsOptional() {
        RawEnvelope plain = RawEnvelope.of(1, 5, SourceTypes.PLATFORM_BROKER, "devices/esp32-co2-02/telemetry", BODY,
                Instant.parse("2026-10-04T01:00:00Z"), "data2flow-ingress-0", "sha256:abc");
        assertThat(plain.signatureStatus()).isNull();
        RawEnvelope verified = plain.withSignature(SignatureStatus.VERIFIED, BODY);
        JsonNode tree = codec.toTree(verified);
        assertThat(tree.get("signatureStatus").asString()).isEqualTo("VERIFIED");
        MessageSchemas.assertValid(verified);
        RawEnvelope read = codec.read(codec.write(verified), RawEnvelope.class);
        assertThat(read).isEqualTo(verified).hasSameHashCodeAs(verified);
        assertThat(read).isNotEqualTo(plain);
        assertThat(read.toString()).contains("signatureStatus=VERIFIED");
        assertThat(codec.read(codec.write(plain), RawEnvelope.class).signatureStatus()).isNull();
        assertThat(plain.asVirtual(3L).withSignature(SignatureStatus.INVALID, BODY).asVirtual(4L).signatureStatus())
                .isEqualTo(SignatureStatus.INVALID);
    }
}
