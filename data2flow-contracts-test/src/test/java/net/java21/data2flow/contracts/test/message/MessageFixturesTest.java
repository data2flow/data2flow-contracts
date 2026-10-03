package net.java21.data2flow.contracts.test.message;

import net.java21.data2flow.contracts.message.CanonicalTelemetry;
import net.java21.data2flow.contracts.message.MessageCodec;
import net.java21.data2flow.contracts.message.MessageSchemas;
import net.java21.data2flow.contracts.message.RawEnvelope;
import net.java21.data2flow.contracts.messaging.DedupKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ING-02.01 TC-ING-033·ING-05.01 TC-ING-065: 공유 픽스처는 스키마를 통과하고 계약 record로 읽힌다 */
class MessageFixturesTest {

    private final MessageCodec codec = MessageCodec.create();
    static final List<String> TELEMETRY = MessageFixtures.CANONICAL_TELEMETRY;
    static final List<String> RAW = MessageFixtures.RAW_ENVELOPE;

    @ParameterizedTest
    @FieldSource("TELEMETRY")
    @DisplayName("ING-02.01 TC-ING-033 표준 텔레메트리 픽스처가 canonical-telemetry.v1.json을 통과하고 다시 써도 스키마를 통과한다")
    void telemetryFixtures(String name) {
        MessageSchemas.assertValid(MessageSchemas.CANONICAL_TELEMETRY,
                codec.mapper().readTree(MessageFixtures.canonicalTelemetryJson(name)));
        CanonicalTelemetry t = MessageFixtures.canonicalTelemetry(name);
        MessageSchemas.assertValid(t);
        assertThat(codec.read(codec.write(t), CanonicalTelemetry.class)).isEqualTo(t);
    }

    @ParameterizedTest
    @FieldSource("RAW")
    @DisplayName("ING-01.01 TC-ING-001 원본 봉투 픽스처가 raw-envelope.v1.json을 통과한다")
    void rawFixtures(String name) {
        MessageSchemas.assertValid(MessageSchemas.RAW_ENVELOPE, codec.mapper().readTree(MessageFixtures.rawEnvelopeJson(name)));
        RawEnvelope raw = MessageFixtures.rawEnvelope(name);
        MessageSchemas.assertValid(raw);
    }

    @Test
    @DisplayName("DEV-03.02 아카데미 실측 6종 픽스처는 기기별 측정 항목을 담는다(research/01 §4)")
    void academyFixtures() {
        assertThat(MessageFixtures.ACADEMY_TELEMETRY).hasSize(6);
        assertThat(MessageFixtures.canonicalTelemetry("academy-am107").metrics()).extracting(CanonicalTelemetry.Metric::key)
                .containsExactly("co2", "tvoc", "pressure", "illumination", "infrared", "activity", "temperature", "humidity");
        assertThat(MessageFixtures.canonicalTelemetry("academy-ws302").metric("LAeq").unit()).isEqualTo("dB");
        assertThat(MessageFixtures.canonicalTelemetry("academy-am103").metric("battery").value()).isEqualTo(1.0);
        CanonicalTelemetry sim = MessageFixtures.canonicalTelemetry("virtual-pending-late");
        assertThat(sim.virtual()).isTrue();
        assertThat(sim.late()).isTrue();
        assertThat(sim.deviceStatus()).isEqualTo(CanonicalTelemetry.DeviceStatus.PENDING);
        assertThat(sim.metric("thi").derived()).isTrue();
    }

    @Test
    @DisplayName("ING-05.01 TC-ING-065 모르는 필드가 섞인 픽스처도 같은 값으로 읽히고 meta 확장 키는 보존된다")
    void unknownFieldsFixture() {
        CanonicalTelemetry withUnknown = MessageFixtures.canonicalTelemetry(MessageFixtures.TELEMETRY_WITH_UNKNOWN_FIELDS);
        CanonicalTelemetry plain = MessageFixtures.canonicalTelemetry("academy-ws302");
        assertThat(withUnknown.metrics()).isEqualTo(plain.metrics());
        assertThat(withUnknown.link()).isEqualTo(plain.link());
        assertThat(withUnknown.meta().extra()).containsKey("heartbeat");
        assertThat(MessageFixtures.allCanonicalTelemetryJson()).containsOnlyKeys(MessageFixtures.CANONICAL_TELEMETRY);
    }

    @Test
    @DisplayName("ING-04.04 ChirpStack 원본 픽스처의 중복 키는 deduplicationId에서 나온다")
    void chirpStackUplink() {
        RawEnvelope raw = MessageFixtures.rawEnvelope("chirpstack-ws302-uplink");
        assertThat(new String(MessageFixtures.chirpStackUplinkPayload(), StandardCharsets.UTF_8)).contains("\"devEui\":\"24e124743d012436\"");
        assertThat(DedupKeys.detect(raw.sourceId(), raw.topic(), raw.payload())).isEqualTo(raw.dedupKey());
        assertThat(MessageFixtures.rawEnvelope("simulation-virtual").virtual()).isTrue();
        assertThatThrownBy(() -> MessageFixtures.rawEnvelope("nope")).isInstanceOf(IllegalArgumentException.class);
    }
}
