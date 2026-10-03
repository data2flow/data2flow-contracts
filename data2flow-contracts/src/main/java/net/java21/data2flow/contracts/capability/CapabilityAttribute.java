package net.java21.data2flow.contracts.capability;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 기능의 상태 속성 하나(ACT-01.01: 이름, 타입, 단위, 범위).
 *
 * @param name       속성 이름(camelCase), 예: {@code targetTemperature}
 * @param type       값 타입
 * @param enumValues {@code type=enum}일 때 허용 값(JSON {@code enum})
 * @param unit       단위(예: {@code °C}, {@code %}). 없으면 null
 * @param min        최솟값(number·integer). 없으면 null
 * @param max        최댓값(number·integer). 없으면 null
 * @param step       값 간격(예: 0.5). 없으면 null
 * @param readOnly   보고만 되고 명령으로 설정할 수 없는 속성(예: currentTemperature). 없으면 false
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CapabilityAttribute(String name, AttributeType type, @JsonProperty("enum") List<String> enumValues,
                                  String unit, Double min, Double max, Double step, Boolean readOnly) {

    public CapabilityAttribute {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("속성 이름은 필수입니다");
        }
        if (type == null) {
            throw new IllegalArgumentException(name + ": 속성 타입은 필수입니다");
        }
        readOnly = Boolean.TRUE.equals(readOnly);
        enumValues = enumValues == null ? null : List.copyOf(enumValues);
        if (type == AttributeType.ENUM && (enumValues == null || enumValues.isEmpty())) {
            throw new IllegalArgumentException(name + ": enum 속성은 허용 값이 필요합니다");
        }
        if (min != null && max != null && min > max) {
            throw new IllegalArgumentException(name + ": min이 max보다 큽니다");
        }
        if (step != null && step <= 0) {
            throw new IllegalArgumentException(name + ": step은 0보다 커야 합니다");
        }
    }

    /** 이 속성의 기본 범위를 제약 모양으로(모델 제약·절대 한계와 교집합을 낼 때) */
    public AttributeConstraint range() {
        return new AttributeConstraint(min, max, enumValues);
    }
}
