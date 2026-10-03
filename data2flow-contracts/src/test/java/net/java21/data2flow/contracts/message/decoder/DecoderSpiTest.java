package net.java21.data2flow.contracts.message.decoder;

import net.java21.data2flow.contracts.message.MessageCodec;
import net.java21.data2flow.contracts.message.RawEnvelope;
import net.java21.data2flow.contracts.message.SourceTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ING-02.01: 디코더 SPI(PayloadDecoder → DecodedUplink) */
class DecoderSpiTest {

    /** 문서 single-value 디코더와 같은 일을 하는 최소 구현(설정: externalId, key) */
    static final class SingleValue implements PayloadDecoder {
        @Override
        public String key() {
            return DecoderKeys.SINGLE_VALUE;
        }

        @Override
        public String version() {
            return "1";
        }

        @Override
        public DecodedUplink decode(RawEnvelope raw, JsonNode config) throws DecodeException {
            String text = new String(raw.payload(), StandardCharsets.UTF_8).trim();
            try {
                return new DecodedUplink(config.get("externalId").asString(), null,
                        List.of(DecodedValue.of(config.get("key").asString(), Double.parseDouble(text))), null, Map.of());
            } catch (NumberFormatException e) {
                throw new DecodeException(key(), "숫자가 아닙니다: " + text, e);
            }
        }
    }

    private final JsonNode config = MessageCodec.create().mapper().readTree("{\"externalId\":\"dev-1\",\"key\":\"temperature\"}");

    private RawEnvelope raw(String body) {
        return RawEnvelope.of(1, 3, SourceTypes.MQTT_SUBSCRIBE, "t", body.getBytes(StandardCharsets.UTF_8),
                Instant.EPOCH, "i", "k");
    }

    @Test
    @DisplayName("ING-02.01 TC-ING-032 디코더는 원본을 기기 식별 전 표준 형태로 바꾸고, 해석 불가는 ING_DECODE_FAILED")
    void decoderContract() throws Exception {
        PayloadDecoder decoder = new SingleValue();
        DecodedUplink up = decoder.decode(raw("22.5"), config);
        assertThat(up.externalId()).isEqualTo("dev-1");
        assertThat(up.measuredAt()).isNull();
        assertThat(up.values()).singleElement().satisfies(v -> {
            assertThat(v.isNumeric()).isTrue();
            assertThat(v.asDouble()).isEqualTo(22.5);
        });
        assertThatThrownBy(() -> decoder.decode(raw("open"), config))
                .isInstanceOfSatisfying(DecodeException.class, e -> assertThat(e.decoderKey()).isEqualTo("single-value"));
        assertThat(DecodeException.RESULT_CODE).isEqualTo("ING_DECODE_FAILED");
        assertThat(new DecodeException("k", "m").getMessage()).isEqualTo("m");
    }

    @Test
    @DisplayName("ING-02.06 비숫자 값(open/close)은 문자열로 넘겨 상태 매핑 단계가 숫자로 바꾼다")
    void nonNumericValues() {
        DecodedValue door = new DecodedValue("magnet_status", "open", null);
        assertThat(door.isNumeric()).isFalse();
        assertThatThrownBy(door::asDouble).isInstanceOf(IllegalStateException.class);
        assertThat(new DecodedValue("alarm", true, null).value()).isEqualTo(true);
        assertThatThrownBy(() -> new DecodedValue("x", List.of(1), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DecodedValue(" ", 1, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ING-02.01 externalId는 1~128자")
    void externalIdLength() {
        assertThatThrownBy(() -> new DecodedUplink("x".repeat(129), null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DecodedUplink(null, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        DecodedUplink ok = new DecodedUplink("x", Instant.EPOCH, null, null, null);
        assertThat(ok.values()).isEmpty();
        assertThat(ok.tags()).isEmpty();
    }

    @Test
    @DisplayName("ING-02.01 TC-ING-032 디코더 키: 기본 3종과 script:{id}@v{n}")
    void decoderKeys() {
        assertThat(DecoderKeys.script(42, 3)).isEqualTo("script:42@v3");
        assertThat(DecoderKeys.parseScript("script:42@v3")).hasValue(new DecoderKeys.ScriptVersion(42, 3));
        assertThat(DecoderKeys.parseScript(DecoderKeys.CHIRPSTACK_V4)).isEmpty();
        assertThat(DecoderKeys.parseScript(null)).isEmpty();
        assertThat(DecoderKeys.GENERIC_JSON).isEqualTo("generic-json");
    }
}
