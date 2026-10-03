package net.java21.data2flow.contracts.capability;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 속성 하나의 값 제약: 범위와 허용 값(ACT-01.03 모델 제약 {@code {targetTemperature:{min:18, max:30}, mode:{enum:[cool, heat]}}},
 * ACT-06.04 조직 절대 한계 {@code {Thermostat:{targetTemperature:{min:18, max:28}}}}). 비어 있는 칸은 제약 없음이다.
 *
 * @param min        최솟값. 없으면 null
 * @param max        최댓값. 없으면 null
 * @param enumValues 허용 값. 없으면 null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttributeConstraint(Double min, Double max, @JsonProperty("enum") List<String> enumValues) {

    /** 제약 없음 */
    public static final AttributeConstraint NONE = new AttributeConstraint(null, null, null);

    public AttributeConstraint {
        enumValues = enumValues == null ? null : List.copyOf(enumValues);
        if (min != null && max != null && min > max) {
            throw new IllegalArgumentException("min이 max보다 큽니다: " + min + " > " + max);
        }
    }

    public static AttributeConstraint range(double min, double max) {
        return new AttributeConstraint(min, max, null);
    }

    public static AttributeConstraint oneOf(List<String> values) {
        return new AttributeConstraint(null, null, values);
    }

    /**
     * 두 제약을 모두 만족하는 범위(API-ACT-03 effectiveConstraints = 모델 제약 ∩ 조직 한계). 겹치지 않으면
     * {@link IllegalArgumentException}.
     */
    public AttributeConstraint intersect(AttributeConstraint other) {
        if (other == null) {
            return this;
        }
        Double lo = pick(min, other.min, true);
        Double hi = pick(max, other.max, false);
        List<String> values;
        if (enumValues == null) {
            values = other.enumValues;
        } else if (other.enumValues == null) {
            values = enumValues;
        } else {
            values = new ArrayList<>(enumValues);
            values.retainAll(other.enumValues);
        }
        return new AttributeConstraint(lo, hi, values);
    }

    /**
     * 이 제약이 {@code outer} 안에 들어가는지(BR-ACT-09: 조직 절대 한계는 모델 제약보다 좁게만, 아니면 LIMIT_WIDER_THAN_MODEL).
     * {@code outer}가 정한 칸을 이 제약이 비워 두면 더 넓은 것으로 본다.
     */
    public boolean within(AttributeConstraint outer) {
        if (outer == null) {
            return true;
        }
        if (outer.min != null && (min == null || min < outer.min)) {
            return false;
        }
        if (outer.max != null && (max == null || max > outer.max)) {
            return false;
        }
        return outer.enumValues == null || (enumValues != null && outer.enumValues.containsAll(enumValues));
    }

    private static Double pick(Double a, Double b, boolean larger) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return larger ? Double.valueOf(Math.max(a, b)) : Double.valueOf(Math.min(a, b));
    }

    /** 숫자 값이 범위 안인지 */
    public boolean allows(double value) {
        return (min == null || value >= min) && (max == null || value <= max);
    }

    /** 문자열 값이 허용 값 안인지 */
    public boolean allows(String value) {
        return enumValues == null || enumValues.contains(value);
    }

    @JsonIgnore
    public boolean isEmpty() {
        return min == null && max == null && enumValues == null;
    }
}
