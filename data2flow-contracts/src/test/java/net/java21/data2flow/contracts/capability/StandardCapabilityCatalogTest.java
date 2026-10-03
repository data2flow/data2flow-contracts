package net.java21.data2flow.contracts.capability;

import net.java21.data2flow.contracts.message.MessageCodec;
import net.java21.data2flow.contracts.message.MessageSchemas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.FieldSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ACT-01.01·01.02·01.04: 표준 기능 카탈로그(골든 JSON)와 기능 정의 스키마, 사용자 정의 이름 규칙(BR-ACT-22) */
class StandardCapabilityCatalogTest {

    private static final JsonMapper MAPPER = MessageCodec.newMapper();
    static final List<String> NAMES = StandardCapabilities.NAMES;

    @Test
    @DisplayName("ACT-01.02 TC-ACT-002 기본 기능 7종(Switch·Thermostat·FanSpeed·Ventilation·Dimmer·Lock·Contact)과 Matter 대응")
    void catalog() {
        assertThat(StandardCapabilities.all()).extracting(CapabilityDefinition::name)
                .containsExactly("Switch", "Thermostat", "FanSpeed", "Ventilation", "Dimmer", "Lock", "Contact");
        assertThat(StandardCapabilities.all()).allSatisfy(d -> {
            assertThat(d.standard()).isTrue();
            assertThat(d.version()).isEqualTo(1);
            assertThat(d.matterCluster()).isNotBlank();
        });
        assertThat(StandardCapabilities.all()).extracting(CapabilityDefinition::matterCluster)
                .containsExactly("OnOff", "Thermostat", "FanControl", "FanControl", "LevelControl", "DoorLock", "BooleanState");
    }

    @ParameterizedTest
    @FieldSource("NAMES")
    @DisplayName("ACT-01.01 TC-ACT-002 기능 정의는 JSON 스키마(capability-definition.v1.json)로 검증되고 같은 값으로 다시 읽힌다")
    void definitionsMatchSchema(String name) throws Exception {
        JsonNode golden;
        try (InputStream in = getClass().getResourceAsStream(StandardCapabilities.resourcePath(name))) {
            golden = MAPPER.readTree(in);
        }
        MessageSchemas.assertValid(MessageSchemas.CAPABILITY_DEFINITION, golden);
        CapabilityDefinition d = StandardCapabilities.get(name);
        MessageSchemas.assertValid(MessageSchemas.CAPABILITY_DEFINITION, MAPPER.valueToTree(d));
        assertThat(MAPPER.treeToValue(MAPPER.valueToTree(d), CapabilityDefinition.class)).isEqualTo(d);
    }

    @ParameterizedTest
    @FieldSource("NAMES")
    @DisplayName("ACT-01.01 TC-ACT-002 명령 인자 스키마는 유효한 JSON Schema이고 속성(sets)·범위와 일치한다")
    void commandArgsSchemasAgreeWithAttributes(String name) {
        CapabilityDefinition d = StandardCapabilities.get(name);
        for (CapabilityCommand c : d.commands()) {
            JsonNode props = c.args().get("properties");
            assertThat(props.propertyNames()).containsExactlyInAnyOrderElementsOf(c.sets());
            assertThat(c.args().get("additionalProperties").asBoolean()).isFalse();
            for (String set : c.sets()) {
                CapabilityAttribute a = d.attribute(set).orElseThrow();
                JsonNode p = props.get(set);
                switch (a.type()) {
                    case BOOLEAN -> assertThat(p.get("type").asString()).isEqualTo("boolean");
                    case ENUM -> assertThat(MAPPER.treeToValue(p.get("enum"), List.class)).isEqualTo(a.enumValues());
                    case NUMBER, INTEGER -> {
                        assertThat(p.get("type").asString()).isEqualTo(a.type() == AttributeType.NUMBER ? "number" : "integer");
                        assertThat(p.has("minimum") ? p.get("minimum").asDouble() : null).isEqualTo(a.min());
                        assertThat(p.has("maximum") ? p.get("maximum").asDouble() : null).isEqualTo(a.max());
                    }
                    default -> throw new AssertionError(a);
                }
            }
            // 인자 스키마 자체를 JSON Schema로 써서 샘플을 검증할 수 있다
            assertThat(MessageSchemas.validateWithSchema(c.args(), MAPPER.createObjectNode()).isEmpty()).isEqualTo(c.minArgs() == 0
                    && c.requiredArgs().isEmpty());
        }
    }

    @Test
    @DisplayName("ACT-01.02 Thermostat: mode enum 6개, targetTemperature 5~35 °C step 0.5, currentTemperature 읽기 전용, 냉방 시 15분 안 하강 기대")
    void thermostat() {
        CapabilityDefinition t = StandardCapabilities.get(StandardCapabilities.THERMOSTAT);
        assertThat(t.attribute("mode").orElseThrow().enumValues()).containsExactly("off", "cool", "heat", "dry", "fan", "auto");
        CapabilityAttribute target = t.attribute("targetTemperature").orElseThrow();
        assertThat(target.unit()).isEqualTo("°C");
        assertThat(target.range()).isEqualTo(AttributeConstraint.range(5, 35));
        assertThat(target.step()).isEqualTo(0.5);
        assertThat(t.attribute("currentTemperature").orElseThrow().readOnly()).isTrue();
        CapabilityCommand set = t.command("set").orElseThrow();
        assertThat(set.sets()).containsExactly("mode", "targetTemperature");
        assertThat(set.minArgs()).isEqualTo(1);
        assertThat(set.requiredArgs()).isEmpty();
        assertThat(t.expectedEffects()).hasSize(2).first().satisfies(e -> {
            assertThat(e.when().args()).containsEntry("mode", "cool");
            assertThat(e.direction()).isEqualTo(ExpectedEffect.Direction.DOWN);
            assertThat(e.withinMinutes()).isEqualTo(15);
        });
        assertThat(StandardCapabilities.get("Switch").command("set").orElseThrow().requiredArgs()).containsExactly("on");
        assertThat(StandardCapabilities.get("Contact").stateOnly()).isTrue();
        assertThat(StandardCapabilities.get("Contact").command("set")).isEmpty();
        assertThat(t.stateOnly()).isFalse();
        assertThat(StandardCapabilities.find("Nope")).isEmpty();
        assertThatThrownBy(() -> StandardCapabilities.get("Nope")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "custom.Humidifier, true, false",
            "custom.Dehumidifier2, true, false",
            "Switch, false, true",
            "switch, false, true",
            "custom., false, false",
            "custom.1abc, false, false",
            "Humidifier, false, false",
            "custom.Hum-idifier, false, false"
    })
    @DisplayName("ACT-01.04 TC-ACT-012 BR-ACT-22 사용자 정의 기능 이름은 custom.으로 시작하고 표준 이름은 예약어다")
    void customNames(String name, boolean validCustom, boolean reserved) {
        assertThat(StandardCapabilities.isValidCustomName(name)).isEqualTo(validCustom);
        assertThat(StandardCapabilities.isStandardName(name)).isEqualTo(reserved);
        if (validCustom) {
            StandardCapabilities.requireCustomName(name);
        } else {
            assertThatThrownBy(() -> StandardCapabilities.requireCustomName(name)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(reserved ? "표준 기능 이름" : "custom.");
        }
        assertThat(StandardCapabilities.isValidCustomName("custom." + "a".repeat(60))).isFalse();
        assertThat(StandardCapabilities.isValidCustomName(null)).isFalse();
        assertThat(StandardCapabilities.isStandardName(null)).isFalse();
    }

    @Test
    @DisplayName("ACT-01.04 TC-ACT-008 사용자 정의 기능(custom.Humidifier targetHumidity 30~70)을 카탈로그에 더하면 표준과 함께 찾는다")
    void customCatalog() {
        CapabilityDefinition humidifier = humidifier();
        MessageSchemas.assertValid(MessageSchemas.CAPABILITY_DEFINITION, MAPPER.valueToTree(humidifier));
        CapabilityCatalog catalog = CapabilityCatalog.of(List.of(humidifier));
        assertThat(catalog.find("custom.Humidifier")).contains(humidifier);
        assertThat(catalog.find("Thermostat")).isPresent();
        assertThat(catalog.all()).hasSize(8);
        assertThat(CapabilityCatalog.standard().all()).hasSize(7);
        assertThat(CapabilityCatalog.standard().find("custom.Humidifier")).isEmpty();
        CapabilityDefinition fakeStandard = new CapabilityDefinition("Switch", 2, false, null, List.of(), List.of(), null);
        assertThatThrownBy(() -> CapabilityCatalog.of(List.of(fakeStandard))).isInstanceOf(IllegalArgumentException.class);
        CapabilityDefinition flagged = new CapabilityDefinition("custom.X", 1, true, null, List.of(), List.of(), null);
        assertThatThrownBy(() -> CapabilityCatalog.of(List.of(flagged))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ACT-01.01 정의가 스스로 맞지 않으면 만들 수 없다(없는·읽기 전용 속성 설정, 이름 중복, 범위 역전)")
    void definitionInvariants() {
        CapabilityAttribute ro = new CapabilityAttribute("battery", AttributeType.NUMBER, null, "%", 0d, 100d, null, true);
        CapabilityCommand setRo = new CapabilityCommand("set", List.of("battery"), null);
        assertThatThrownBy(() -> new CapabilityDefinition("custom.A", 1, false, null, List.of(ro), List.of(setRo), null))
                .hasMessageContaining("읽기 전용");
        CapabilityCommand setMissing = new CapabilityCommand("set", List.of("nope"), null);
        assertThatThrownBy(() -> new CapabilityDefinition("custom.A", 1, false, null, List.of(ro), List.of(setMissing), null))
                .hasMessageContaining("없는 속성");
        assertThatThrownBy(() -> new CapabilityDefinition("custom.A", 1, false, null, List.of(ro, ro), List.of(), null))
                .hasMessageContaining("속성 이름");
        CapabilityCommand noop = new CapabilityCommand("noop", List.of(), null);
        assertThatThrownBy(() -> new CapabilityDefinition("custom.A", 1, false, null, List.of(), List.of(noop, noop), null))
                .hasMessageContaining("명령 이름");
        assertThatThrownBy(() -> new CapabilityDefinition(" ", 1, false, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CapabilityDefinition("custom.A", 0, false, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CapabilityAttribute("x", AttributeType.ENUM, null, null, null, null, null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CapabilityAttribute("x", AttributeType.NUMBER, null, null, 5d, 1d, null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CapabilityAttribute("x", AttributeType.NUMBER, null, null, null, null, 0d, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CapabilityAttribute(null, AttributeType.NUMBER, null, null, null, null, null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CapabilityAttribute("x", null, null, null, null, null, null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CapabilityCommand(" ", List.of(), null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new CapabilityCommand("x", null, null).minArgs()).isZero();
        assertThat(new CapabilityCommand("x", null, null).requiredArgs()).isEmpty();
        assertThat(MAPPER.readValue("{\"name\":\"x\",\"type\":\"geo\",\"readOnly\":true}", CapabilityAttribute.class).type())
                .isEqualTo(AttributeType.UNKNOWN);
    }

    static CapabilityDefinition humidifier() {
        return MAPPER.readValue("""
                {"name":"custom.Humidifier","version":1,"standard":false,
                 "attributes":[{"name":"targetHumidity","type":"integer","unit":"%","min":30,"max":70,"step":1}],
                 "commands":[{"name":"set","sets":["targetHumidity"],"args":{"type":"object","required":["targetHumidity"],
                   "additionalProperties":false,"properties":{"targetHumidity":{"type":"integer","minimum":30,"maximum":70}}}}]}
                """, CapabilityDefinition.class);
    }

    static Map<String, Object> args(Object... kv) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
