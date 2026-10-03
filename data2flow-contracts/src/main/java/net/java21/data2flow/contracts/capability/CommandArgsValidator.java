package net.java21.data2flow.contracts.capability;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 명령 인자 검증(ACT-01.01·01.03·06.04, BR-ACT-01의 "기능 스키마 → 모델 제약 → 조직 절대 한계" 세 단계).
 *
 * <p>제어 창구(action)가 권위 있게 검사하고, core-api·화면·flow-engine 편집기·AI는 같은 함수로 미리 검사해 사용자에게 빨리 알린다.
 * 외부 라이브러리 없이 동작한다(운영 경로에서 써도 된다).
 *
 * <pre>{@code
 * CommandValidation r = CommandArgsValidator.validate(catalog, "Thermostat", "set", Map.of("targetTemperature", 31),
 *         Map.of("targetTemperature", AttributeConstraint.range(18, 30)),     // 모델 제약
 *         Map.of("targetTemperature", AttributeConstraint.range(18, 28)));    // 조직 절대 한계
 * r.resultCode();   // Optional[COMMAND_ARG_OUT_OF_RANGE], violations[0].min=18, max=30
 * }</pre>
 */
public final class CommandArgsValidator {

    private CommandArgsValidator() {
    }

    /** 카탈로그에서 기능을 찾아 검증한다. 없으면 {@code CAPABILITY_NOT_SUPPORTED} */
    public static CommandValidation validate(CapabilityCatalog catalog, String capability, String command,
                                             Map<String, ?> args, Map<String, AttributeConstraint> modelConstraints,
                                             Map<String, AttributeConstraint> absoluteLimits) {
        Optional<CapabilityDefinition> definition = catalog.find(capability);
        if (definition.isEmpty()) {
            return fail(ArgViolation.of("capability", ArgViolation.Reason.CAPABILITY_NOT_SUPPORTED,
                    "카탈로그에 없는 기능입니다: " + capability));
        }
        return validate(definition.get(), command, args, modelConstraints, absoluteLimits);
    }

    /** 기능 스키마만 검사한다(모델·조직 제약 없음) */
    public static CommandValidation validate(CapabilityDefinition definition, String command, Map<String, ?> args) {
        return validate(definition, command, args, Map.of(), Map.of());
    }

    /**
     * @param modelConstraints 기기 모델의 기능별 제약(속성 이름 → 제약). 없으면 빈 맵
     * @param absoluteLimits   조직 절대 한계 중 이 기능의 것(속성 이름 → 제약). 없으면 빈 맵
     */
    public static CommandValidation validate(CapabilityDefinition definition, String command, Map<String, ?> args,
                                             Map<String, AttributeConstraint> modelConstraints,
                                             Map<String, AttributeConstraint> absoluteLimits) {
        Optional<CapabilityCommand> found = definition.command(command);
        if (found.isEmpty()) {
            return fail(ArgViolation.of("command", ArgViolation.Reason.COMMAND_NOT_SUPPORTED,
                    definition.name() + "에 없는 명령입니다: " + command));
        }
        Map<String, ?> a = args == null ? Map.of() : args;
        List<ArgViolation> schema = schema(definition, found.get(), a);
        if (!schema.isEmpty()) {
            return new CommandValidation(schema);
        }
        List<ArgViolation> model = constraints(a, modelConstraints, ArgViolation.Reason.MODEL_CONSTRAINT, "모델 제약");
        if (!model.isEmpty()) {
            return new CommandValidation(model);
        }
        return new CommandValidation(constraints(a, absoluteLimits, ArgViolation.Reason.ABSOLUTE_LIMIT, "조직 절대 한계"));
    }

    private static List<ArgViolation> schema(CapabilityDefinition definition, CapabilityCommand command, Map<String, ?> args) {
        List<ArgViolation> out = new ArrayList<>();
        for (String key : args.keySet()) {
            if (!command.sets().contains(key)) {
                out.add(ArgViolation.of("args." + key, ArgViolation.Reason.UNKNOWN_ARG,
                        definition.name() + "." + command.name() + "이(가) 설정하지 않는 인자입니다: " + key));
            }
        }
        for (String required : command.requiredArgs()) {
            if (args.get(required) == null) {
                out.add(ArgViolation.of("args." + required, ArgViolation.Reason.MISSING_ARG, "필수 인자입니다: " + required));
            }
        }
        if (args.size() < command.minArgs()) {
            out.add(ArgViolation.of("args", ArgViolation.Reason.TOO_FEW_ARGS,
                    "인자가 " + command.minArgs() + "개 이상 필요합니다: " + command.sets()));
        }
        for (Map.Entry<String, ?> e : args.entrySet()) {
            if (!command.sets().contains(e.getKey()) || e.getValue() == null) {
                continue;
            }
            CapabilityAttribute attribute = definition.attribute(e.getKey()).orElseThrow();
            ArgViolation v = value(attribute, e.getValue());
            if (v != null) {
                out.add(v);
            }
        }
        return out;
    }

    private static ArgViolation value(CapabilityAttribute attribute, Object value) {
        String field = "args." + attribute.name();
        switch (attribute.type()) {
            case BOOLEAN -> {
                return value instanceof Boolean ? null : wrongType(field, "boolean", value);
            }
            case STRING -> {
                return value instanceof String ? null : wrongType(field, "string", value);
            }
            case ENUM -> {
                if (!(value instanceof String s)) {
                    return wrongType(field, "enum", value);
                }
                return attribute.enumValues().contains(s) ? null : ArgViolation.of(field,
                        ArgViolation.Reason.NOT_ALLOWED_VALUE, "허용 값 " + attribute.enumValues() + " 밖입니다: " + s);
            }
            case NUMBER, INTEGER -> {
                if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) {
                    return wrongType(field, attribute.type().name().toLowerCase(), value);
                }
                BigDecimal d = decimal(n);
                if (attribute.type() == AttributeType.INTEGER && d.stripTrailingZeros().scale() > 0) {
                    return wrongType(field, "integer", value);
                }
                if (!attribute.range().allows(n.doubleValue())) {
                    return new ArgViolation(field, ArgViolation.Reason.OUT_OF_STANDARD_RANGE,
                            attribute.name() + "의 범위 밖입니다: " + n, attribute.min(), attribute.max());
                }
                if (attribute.step() != null) {
                    BigDecimal base = attribute.min() == null ? BigDecimal.ZERO : BigDecimal.valueOf(attribute.min());
                    if (d.subtract(base).remainder(BigDecimal.valueOf(attribute.step())).signum() != 0) {
                        return ArgViolation.of(field, ArgViolation.Reason.STEP_MISMATCH,
                                attribute.name() + "은(는) " + attribute.step() + " 간격이어야 합니다: " + n);
                    }
                }
                return null;
            }
            default -> {
                return ArgViolation.of(field, ArgViolation.Reason.WRONG_TYPE, "알 수 없는 속성 타입입니다: " + attribute.name());
            }
        }
    }

    private static List<ArgViolation> constraints(Map<String, ?> args, Map<String, AttributeConstraint> constraints,
                                                  ArgViolation.Reason reason, String label) {
        List<ArgViolation> out = new ArrayList<>();
        if (constraints == null) {
            return out;
        }
        for (Map.Entry<String, ?> e : args.entrySet()) {
            AttributeConstraint c = constraints.get(e.getKey());
            if (c == null || e.getValue() == null) {
                continue;
            }
            String field = "args." + e.getKey();
            if (e.getValue() instanceof Number n && !c.allows(n.doubleValue())) {
                out.add(new ArgViolation(field, reason, label + " " + range(c) + " 밖입니다: " + n, c.min(), c.max()));
            } else if (e.getValue() instanceof String s && !c.allows(s)) {
                out.add(ArgViolation.of(field, reason, label + " 허용 값 " + c.enumValues() + " 밖입니다: " + s));
            }
        }
        return out;
    }

    private static String range(AttributeConstraint c) {
        return (c.min() == null ? "" : fmt(c.min())) + "~" + (c.max() == null ? "" : fmt(c.max()));
    }

    private static String fmt(double v) {
        return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    private static BigDecimal decimal(Number n) {
        return n instanceof BigDecimal b ? b : new BigDecimal(n.toString());
    }

    private static ArgViolation wrongType(String field, String expected, Object value) {
        return ArgViolation.of(field, ArgViolation.Reason.WRONG_TYPE,
                expected + " 값이어야 합니다: " + (value == null ? "null" : value.getClass().getSimpleName()));
    }

    private static CommandValidation fail(ArgViolation v) {
        return new CommandValidation(List.of(v));
    }
}
