package net.java21.data2flow.contracts.message;

import net.java21.data2flow.contracts.message.CanonicalTelemetry.DeviceStatus;
import net.java21.data2flow.contracts.message.CanonicalTelemetry.GatewayReception;
import net.java21.data2flow.contracts.message.CanonicalTelemetry.Link;
import net.java21.data2flow.contracts.message.CanonicalTelemetry.Meta;
import net.java21.data2flow.contracts.message.CanonicalTelemetry.Metric;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ING-02.01·ING-05.01: 표준 텔레메트리 CanonicalTelemetry v1 계약(EVT-ING-02) */
class CanonicalTelemetryContractTest {

    private final MessageCodec codec = MessageCodec.create();

    /** ING-api.md EVT-ING-02의 예시 그대로(messageId만 실제 UUID) */
    static final String DOC_EXAMPLE = """
            {"v":1,"messageId":"8f3e2c1a-5b6d-4e7f-8a9b-0c1d2e3f4a5b","organizationId":1,"sourceId":3,"externalId":"24e124743d012436",
             "deviceId":17,"deviceStatus":"ACTIVE","modelId":"6","spaceId":31,
             "measuredAt":"2026-10-03T02:40:09.123Z","receivedAt":"2026-10-03T02:40:09.501Z","late":false,"virtual":false,
             "metrics":[{"key":"LAeq","value":30.5,"unit":"dB","quality":0},{"key":"battery","value":55,"unit":"%","quality":0}],
             "link":{"rssi":-33,"snr":13.5,"frameCounter":23518,"gateways":[{"eui":"24e124fffef79304","rssi":-33,"snr":13.5}]},
             "meta":{"tags":{"location":"실습실","point":"강단"},"decoder":{"key":"chirpstack-v4","version":"1"}},
             "rawMessageId":8812345}
            """;

    static CanonicalTelemetry sample() {
        return CanonicalTelemetry.builder()
                .messageId(UUID.fromString("8f3e2c1a-5b6d-4e7f-8a9b-0c1d2e3f4a5b"))
                .organizationId(1).sourceId(3).externalId("24e124743d012436").deviceId(17)
                .deviceStatus(DeviceStatus.ACTIVE).modelId("6").spaceId(31L)
                .measuredAt(Instant.parse("2026-10-03T02:40:09.123Z"))
                .receivedAt(Instant.parse("2026-10-03T02:40:09.501Z"))
                .metric(Metric.of("LAeq", 30.5, "dB"))
                .metric(Metric.of("battery", 55, "%"))
                .link(new Link(-33.0, 13.5, 23518L, List.of(new GatewayReception("24e124fffef79304", -33.0, 13.5))))
                .meta(new Meta(Map.of("location", "실습실", "point", "강단"),
                        new CanonicalTelemetry.DecoderRef("chirpstack-v4", "1"), null))
                .rawMessageId(8812345)
                .build();
    }

    @Test
    @DisplayName("ING-02.01 TC-ING-033 문서 예시 JSON이 canonical-telemetry.v1.json을 통과하고 같은 record로 읽힌다")
    void docExampleMatchesSchemaAndRecord() {
        JsonNode tree = codec.mapper().readTree(DOC_EXAMPLE);
        MessageSchemas.assertValid(MessageSchemas.CANONICAL_TELEMETRY, tree);
        assertThat(codec.read(DOC_EXAMPLE, CanonicalTelemetry.class)).isEqualTo(sample());
    }

    @Test
    @DisplayName("ING-02.01 TC-ING-033 쓴 JSON이 스키마를 통과하고 v·messageId가 있으며 선택 필드 null은 생략된다")
    void writtenJsonMatchesSchema() {
        CanonicalTelemetry minimal = CanonicalTelemetry.builder().organizationId(1).sourceId(3).externalId("e")
                .deviceId(17).measuredAt(Instant.parse("2026-10-03T00:00:00Z"))
                .receivedAt(Instant.parse("2026-10-03T00:00:01Z")).rawMessageId(1).build();
        MessageSchemas.assertValid(sample());
        MessageSchemas.assertValid(minimal);
        String json = codec.writeAsString(minimal);
        assertThat(json).contains("\"v\":1", "\"messageId\":\"", "\"measuredAt\":\"2026-10-03T00:00:00Z\"", "\"metrics\":[]")
                .doesNotContain("modelId", "spaceId", "link", "meta", "derived");
        assertThat(MessageSchemas.fileOf(CanonicalTelemetry.class)).isEqualTo(MessageSchemas.CANONICAL_TELEMETRY);
    }

    @Test
    @DisplayName("ING-05.01 TC-ING-065 소비자는 모르는 필드(최상위·측정값·link·meta)를 무시하고 읽는다")
    void unknownFieldsAreIgnored() {
        ObjectNode tree = (ObjectNode) codec.mapper().readTree(DOC_EXAMPLE);
        tree.put("newTopLevel", "x");
        ((ObjectNode) tree.get("metrics").get(0)).put("confidence", 0.9);
        ((ObjectNode) tree.get("link")).put("dataRate", "SF7");
        ((ObjectNode) tree.get("link").get("gateways").get(0)).put("channel", 3);
        ((ObjectNode) tree.get("meta").get("decoder")).put("hash", "abc");
        CanonicalTelemetry read = codec.read(codec.mapper().writeValueAsBytes(tree), CanonicalTelemetry.class);
        assertThat(read).isEqualTo(sample());
        MessageSchemas.assertValid(MessageSchemas.CANONICAL_TELEMETRY, tree);
    }

    @Test
    @DisplayName("ING-05.01 TC-ING-065 meta의 알려지지 않은 키(하트비트 stages 등)는 보존되어 다시 쓸 때도 남는다")
    void metaExtraIsPreserved() {
        ObjectNode tree = (ObjectNode) codec.mapper().readTree(DOC_EXAMPLE);
        ((ObjectNode) tree.get("meta")).putObject("heartbeat").putArray("stages").addObject()
                .put("name", "ingress").put("at", "2026-10-03T02:40:09.501Z");
        CanonicalTelemetry read = codec.read(codec.mapper().writeValueAsBytes(tree), CanonicalTelemetry.class);
        assertThat(read.meta().extra()).containsKey("heartbeat");
        assertThat(read.meta().tags()).containsEntry("point", "강단");
        JsonNode again = codec.toTree(read);
        assertThat(again.at("/meta/heartbeat/stages/0/name").asString()).isEqualTo("ingress");
        assertThat(again.get("meta").has("extra")).isFalse();
    }

    @Test
    @DisplayName("ING-05.01 TC-ING-065 아는 버전보다 큰 v·v 없음·v=0은 UnsupportedSchemaVersionException")
    void versionIsChecked() {
        for (String v : List.of("2", "0", "\"1\"")) {
            String body = DOC_EXAMPLE.replace("\"v\":1", "\"v\":" + v);
            assertThatThrownBy(() -> codec.read(body, CanonicalTelemetry.class))
                    .isInstanceOf(UnsupportedSchemaVersionException.class)
                    .hasMessageContaining("canonical-telemetry");
        }
        String noV = DOC_EXAMPLE.replace("\"v\":1,", "");
        assertThatThrownBy(() -> codec.read(noV, CanonicalTelemetry.class))
                .isInstanceOfSatisfying(UnsupportedSchemaVersionException.class, e -> {
                    assertThat(e.receivedVersion()).isNull();
                    assertThat(e.supportedVersion()).isEqualTo(1);
                    assertThat(e.schema()).isEqualTo("canonical-telemetry");
                });
        assertThat(codec.versionOf(DOC_EXAMPLE.getBytes())).isEqualTo(1);
        assertThat(codec.versionOf(noV.getBytes())).isNull();
    }

    @Test
    @DisplayName("ING-02.01 TC-ING-033 필수 필드 누락·범위 위반은 MessageFormatException(DLQ 대상)")
    void requiredFieldsAndRangesAreEnforced() {
        for (String removed : List.of("deviceId", "externalId", "measuredAt", "rawMessageId", "organizationId",
                "deviceStatus", "metrics")) {
            ObjectNode tree = (ObjectNode) codec.mapper().readTree(DOC_EXAMPLE);
            tree.remove(removed);
            assertThatThrownBy(() -> codec.read(codec.mapper().writeValueAsBytes(tree), CanonicalTelemetry.class))
                    .as(removed).isInstanceOf(MessageFormatException.class).hasMessageContaining(removed);
            assertThat(MessageSchemas.validate(MessageSchemas.CANONICAL_TELEMETRY, tree)).as(removed).isNotEmpty();
        }
        assertThatThrownBy(() -> codec.read(DOC_EXAMPLE.replace("\"quality\":0}", "\"quality\":6}"), CanonicalTelemetry.class))
                .isInstanceOf(MessageFormatException.class).hasMessageContaining("quality");
        assertThatThrownBy(() -> codec.read(DOC_EXAMPLE.replace("\"value\":30.5", "\"value\":null"), CanonicalTelemetry.class))
                .isInstanceOf(MessageFormatException.class).hasMessageContaining("value");
        assertThatThrownBy(() -> codec.read(DOC_EXAMPLE.replace("\"late\":false", "\"late\":null"), CanonicalTelemetry.class))
                .isInstanceOf(MessageFormatException.class);
        assertThatThrownBy(() -> Metric.of("k".repeat(65), 1, null)).isInstanceOf(MessageFormatException.class);
        assertThatThrownBy(() -> Metric.of("k", Double.NaN, null)).isInstanceOf(MessageFormatException.class);
        assertThatThrownBy(() -> new GatewayReception(" ", null, null)).isInstanceOf(MessageFormatException.class);
        List<Metric> tooMany = new ArrayList<>();
        for (int i = 0; i <= CanonicalTelemetry.MAX_METRICS; i++) {
            tooMany.add(Metric.of("m" + i, i, null));
        }
        assertThatThrownBy(() -> CanonicalTelemetry.builder().organizationId(1).sourceId(1).externalId("e").deviceId(1)
                .measuredAt(Instant.EPOCH).receivedAt(Instant.EPOCH).rawMessageId(1).metrics(tooMany).build())
                .isInstanceOf(MessageFormatException.class).hasMessageContaining("100");
    }

    @Test
    @DisplayName("ING-05.01 TC-ING-063 라우팅 키는 deviceId라서 같은 기기는 같은 파티션")
    void routingKeyIsDeviceId() {
        assertThat(sample().routingKey()).isEqualTo("17");
        assertThat(sample().metric("battery").value()).isEqualTo(55.0);
        assertThat(sample().metric("none")).isNull();
        Metric suspect = Metric.of("co2", 1200, "ppm").withQuality(Quality.SUSPECT);
        assertThat(suspect.quality()).isEqualTo(3);
        assertThat(new Metric("co2", 1.0, null, Quality.NORMAL, true).derived()).isTrue();
    }
}
