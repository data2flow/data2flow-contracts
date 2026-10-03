package net.java21.data2flow.contracts.capability;

import net.java21.data2flow.contracts.message.MessageCodec;
import net.java21.data2flow.contracts.message.MessageSchemas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static net.java21.data2flow.contracts.capability.StandardCapabilityCatalogTest.args;
import static org.assertj.core.api.Assertions.assertThat;

/** ACT-01.01·01.03·06.04·02.01: 명령 인자 검증(기능 스키마 → 모델 제약 → 조직 절대 한계, BR-ACT-01·09) */
class CommandArgsValidatorTest {

    private static final JsonMapper MAPPER = MessageCodec.newMapper();
    private static final CapabilityCatalog CATALOG = CapabilityCatalog.of(List.of(StandardCapabilityCatalogTest.humidifier()));
    private static final Map<String, AttributeConstraint> MODEL = Map.of(
            "targetTemperature", AttributeConstraint.range(18, 30),
            "mode", AttributeConstraint.oneOf(List.of("off", "cool", "heat", "fan")));
    private static final Map<String, AttributeConstraint> LIMIT = Map.of("targetTemperature", AttributeConstraint.range(18, 28));

    private static CommandValidation thermostat(Map<String, Object> args) {
        return CommandArgsValidator.validate(CATALOG, "Thermostat", "set", args, MODEL, LIMIT);
    }

    @Test
    @DisplayName("ACT-02.01 TC-ACT-014 냉방 24℃는 세 단계를 모두 통과한다")
    void validCommand() {
        CommandValidation r = thermostat(args("mode", "cool", "targetTemperature", 24));
        assertThat(r.ok()).isTrue();
        assertThat(r.resultCode()).isEmpty();
        assertThat(r.reason()).isEmpty();
        assertThat(r.fieldErrors()).isEmpty();
        assertThat(CommandArgsValidator.validate(StandardCapabilities.get("Switch"), "set", Map.of("on", true)).ok()).isTrue();
        assertThat(CommandArgsValidator.validate(CATALOG, "custom.Humidifier", "set", Map.of("targetHumidity", 45), null, null).ok())
                .isTrue();
    }

    @Test
    @DisplayName("ACT-01.01 TC-ACT-001 인자 스키마 위반 → COMMAND_ARGS_INVALID(모르는 인자·읽기 전용·필수 누락·개수·타입·허용 값·간격·기본 범위)")
    void schemaViolations() {
        assertReason(thermostat(args("targetTemperature", 24, "fanLevel", 2)), "args.fanLevel", ArgViolation.Reason.UNKNOWN_ARG);
        assertReason(thermostat(args("currentTemperature", 20)), "args.currentTemperature", ArgViolation.Reason.UNKNOWN_ARG);
        assertReason(thermostat(args()), "args", ArgViolation.Reason.TOO_FEW_ARGS);
        assertReason(CommandArgsValidator.validate(StandardCapabilities.get("Switch"), "set", null), "args.on",
                ArgViolation.Reason.MISSING_ARG);
        assertReason(thermostat(args("targetTemperature", "24")), "args.targetTemperature", ArgViolation.Reason.WRONG_TYPE);
        assertReason(thermostat(args("targetTemperature", Double.NaN)), "args.targetTemperature", ArgViolation.Reason.WRONG_TYPE);
        assertReason(thermostat(args("mode", 1)), "args.mode", ArgViolation.Reason.WRONG_TYPE);
        assertReason(thermostat(args("mode", "turbo")), "args.mode", ArgViolation.Reason.NOT_ALLOWED_VALUE);
        assertReason(thermostat(args("targetTemperature", 24.3)), "args.targetTemperature", ArgViolation.Reason.STEP_MISMATCH);
        assertReason(thermostat(args("targetTemperature", 40)), "args.targetTemperature", ArgViolation.Reason.OUT_OF_STANDARD_RANGE);
        assertReason(CommandArgsValidator.validate(StandardCapabilities.get("Switch"), "set", Map.of("on", "true")), "args.on",
                ArgViolation.Reason.WRONG_TYPE);
        assertReason(CommandArgsValidator.validate(StandardCapabilities.get("Dimmer"), "set", Map.of("level", 50.5)), "args.level",
                ArgViolation.Reason.WRONG_TYPE);
        assertThat(CommandArgsValidator.validate(StandardCapabilities.get("Dimmer"), "set", Map.of("level", 50.0)).ok()).isTrue();
        assertThat(CommandArgsValidator.validate(StandardCapabilities.get("Dimmer"), "set", Map.of("level", new BigDecimal("50")))
                .ok()).isTrue();
        CommandValidation many = thermostat(args("mode", "turbo", "targetTemperature", "x"));
        assertThat(many.violations()).hasSize(2);
        assertThat(many.resultCode()).contains("COMMAND_ARGS_INVALID");
        assertThat(many.fieldErrors()).extracting(e -> e.code()).containsExactly("NOT_ALLOWED_VALUE", "WRONG_TYPE");
    }

    @Test
    @DisplayName("ACT-01.03 TC-ACT-006 모델이 지원하지 않는 기능·명령 → CAPABILITY_NOT_SUPPORTED")
    void unsupported() {
        CommandValidation noCapability = CommandArgsValidator.validate(CATALOG, "custom.Nope", "set", Map.of(), null, null);
        assertReason(noCapability, "capability", ArgViolation.Reason.CAPABILITY_NOT_SUPPORTED);
        assertThat(noCapability.resultCode()).contains("CAPABILITY_NOT_SUPPORTED");
        assertReason(CommandArgsValidator.validate(CATALOG, "Thermostat", "setTargetTemperature", Map.of("value", 24), MODEL, LIMIT),
                "command", ArgViolation.Reason.COMMAND_NOT_SUPPORTED);
        assertReason(CommandArgsValidator.validate(CATALOG, "Contact", "set", Map.of("open", true), null, null), "command",
                ArgViolation.Reason.COMMAND_NOT_SUPPORTED);
    }

    @ParameterizedTest
    @CsvSource({"18, true", "30, false", "31, false", "17.5, false", "28, true", "28.5, false"})
    @DisplayName("ACT-01.03 TC-ACT-004·TC-ACT-007 모델 범위 18~30 밖은 COMMAND_ARG_OUT_OF_RANGE(min·max 포함), 조직 한계 18~28이 그다음")
    void modelThenAbsolute(double value, boolean ok) {
        CommandValidation r = thermostat(args("targetTemperature", value));
        assertThat(r.ok()).isEqualTo(ok);
        if (value > 30 || value < 18) {
            assertThat(r.resultCode()).contains("COMMAND_ARG_OUT_OF_RANGE");
            assertThat(r.violations().getFirst().min()).isEqualTo(18.0);
            assertThat(r.violations().getFirst().max()).isEqualTo(30.0);
        } else if (!ok) {
            assertThat(r.resultCode()).contains("COMMAND_ABSOLUTE_LIMIT");
            assertThat(r.violations().getFirst().max()).isEqualTo(28.0);
            assertThat(r.violations().getFirst().message()).contains("18~28");
        }
    }

    @ParameterizedTest
    @CsvSource({"18.0, true", "28.0, true", "17.9, false", "28.1, false"})
    @DisplayName("ACT-06.04 TC-ACT-117 조직 절대 한계 18~28 경계: 18.0·28.0 허용, 17.9·28.1 ABSOLUTE_LIMIT(출처와 무관)")
    void absoluteLimitBoundaries(double value, boolean ok) {
        CapabilityDefinition fine = new CapabilityDefinition("custom.Fine", 1, false, null,
                List.of(new CapabilityAttribute("t", AttributeType.NUMBER, null, "°C", 5d, 35d, null, false)),
                List.of(new CapabilityCommand("set", List.of("t"), MAPPER.readTree("{\"type\":\"object\",\"properties\":{\"t\":{}}}"))),
                null);
        CommandValidation r = CommandArgsValidator.validate(fine, "set", Map.of("t", value), Map.of(),
                Map.of("t", AttributeConstraint.range(18, 28)));
        assertThat(r.ok()).isEqualTo(ok);
        if (!ok) {
            assertThat(r.reason()).contains(ArgViolation.Reason.ABSOLUTE_LIMIT);
        }
    }

    @Test
    @DisplayName("ACT-02.01 TC-ACT-025 BR-ACT-01 여러 위반이 겹치면 앞 단계 사유: 스키마 > 모델 제약 > 절대 한계")
    void stageOrder() {
        assertReason(thermostat(args("mode", "dry", "targetTemperature", 29)), "args.mode", ArgViolation.Reason.MODEL_CONSTRAINT);
        assertThat(thermostat(args("mode", "dry", "targetTemperature", 29)).violations()).hasSize(1);
        assertReason(thermostat(args("mode", "turbo", "targetTemperature", 29)), "args.mode", ArgViolation.Reason.NOT_ALLOWED_VALUE);
        assertThat(ArgViolation.Reason.ABSOLUTE_LIMIT.stage()).isGreaterThan(ArgViolation.Reason.MODEL_CONSTRAINT.stage());
        assertThat(ArgViolation.Reason.MODEL_CONSTRAINT.stage()).isGreaterThan(ArgViolation.Reason.WRONG_TYPE.stage());
    }

    static Stream<Arguments> parity() {
        return Stream.of(
                Arguments.of("Thermostat", Map.of("mode", "cool", "targetTemperature", 24)),
                Arguments.of("Thermostat", Map.of("targetTemperature", 24.5)),
                Arguments.of("Thermostat", Map.of("targetTemperature", 24.3)),
                Arguments.of("Thermostat", Map.of("mode", "turbo")),
                Arguments.of("Thermostat", Map.of()),
                Arguments.of("Thermostat", Map.of("x", 1)),
                Arguments.of("Switch", Map.of("on", true)),
                Arguments.of("Switch", Map.of()),
                Arguments.of("FanSpeed", Map.of("level", -1)),
                Arguments.of("FanSpeed", Map.of("auto", true)),
                Arguments.of("Ventilation", Map.of("level", 4)),
                Arguments.of("Ventilation", Map.of("mode", "auto", "level", 2)),
                Arguments.of("Dimmer", Map.of("level", 101)),
                Arguments.of("Dimmer", Map.of("level", 3.5)),
                Arguments.of("Lock", Map.of("locked", false)),
                Arguments.of("Lock", Map.of("locked", 1)));
    }

    @ParameterizedTest
    @MethodSource("parity")
    @DisplayName("ACT-01.01 TC-ACT-002 내장 검증기와 명령 인자 JSON Schema(networknt)의 판정이 같다")
    void agreesWithJsonSchema(String capability, Map<String, Object> args) {
        CapabilityDefinition d = StandardCapabilities.get(capability);
        boolean ours = CommandArgsValidator.validate(d, "set", args).ok();
        boolean schema = MessageSchemas.validateWithSchema(d.command("set").orElseThrow().args(), MAPPER.valueToTree(args)).isEmpty();
        assertThat(ours).as(capability + " " + args).isEqualTo(schema);
    }

    private static void assertReason(CommandValidation r, String field, ArgViolation.Reason reason) {
        assertThat(r.ok()).isFalse();
        assertThat(r.violations().getFirst().field()).isEqualTo(field);
        assertThat(r.violations().getFirst().reason()).isEqualTo(reason);
        assertThat(r.resultCode()).contains(reason.resultCode());
    }
}
