package net.java21.data2flow.contracts.capability;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 기능별 상태 맵 {@code {capability:{attribute:value}}} 계산(ACT-02.04 desired/reported/delta, AWS Device Shadow 개념).
 * 예: {@code {"Thermostat":{"mode":"cool","targetTemperature":24},"Switch":{"on":true}}}.
 *
 * <p>값 비교는 숫자면 크기로 한다(24와 24.0은 같다). 맵은 바꾸지 않고 새 맵을 돌려준다(불변).
 */
public final class CapabilityStates {

    private CapabilityStates() {
    }

    /** 빈 상태 */
    public static Map<String, Map<String, Object>> empty() {
        return Map.of();
    }

    /**
     * {@code base}에 {@code capability}의 속성 값을 덮어쓴다(목표 상태 설정, BR-ACT-03). 같은 인자를 여러 번 적용해도 결과가 같다.
     * 값이 null인 인자는 무시한다.
     */
    public static Map<String, Map<String, Object>> merge(Map<String, Map<String, Object>> base, String capability,
                                                         Map<String, ?> values) {
        Map<String, Map<String, Object>> out = mutableCopy(base);
        Map<String, Object> attrs = out.computeIfAbsent(capability, k -> new LinkedHashMap<>());
        if (values != null) {
            values.forEach((k, v) -> {
                if (v != null) {
                    attrs.put(k, v);
                }
            });
        }
        return freeze(out);
    }

    /** 여러 기능을 한꺼번에 덮어쓴다(부분 보고 반영) */
    public static Map<String, Map<String, Object>> mergeAll(Map<String, Map<String, Object>> base,
                                                            Map<String, ? extends Map<String, ?>> update) {
        Map<String, Map<String, Object>> out = base == null ? Map.of() : base;
        if (update != null) {
            for (Map.Entry<String, ? extends Map<String, ?>> e : update.entrySet()) {
                out = merge(out, e.getKey(), e.getValue());
            }
        }
        return out;
    }

    /**
     * delta: desired 중 reported와 다른 속성(desired 값으로). 둘이 같으면 빈 맵이다. reported에만 있는 속성은 보지 않는다.
     */
    public static Map<String, Map<String, Object>> delta(Map<String, Map<String, Object>> desired,
                                                         Map<String, Map<String, Object>> reported) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        if (desired == null) {
            return Map.of();
        }
        desired.forEach((capability, attrs) -> {
            Map<String, Object> r = reported == null ? null : reported.get(capability);
            attrs.forEach((attr, value) -> {
                Object actual = r == null ? null : r.get(attr);
                if (!valuesEqual(value, actual)) {
                    out.computeIfAbsent(capability, k -> new LinkedHashMap<>()).put(attr, value);
                }
            });
        });
        return freeze(out);
    }

    /** 명령 인자가 reported에 모두 반영됐는지(APPLIED 판정) */
    public static boolean isApplied(Map<String, Map<String, Object>> reported, String capability, Map<String, ?> args) {
        if (args == null || args.isEmpty()) {
            return true;
        }
        Map<String, Object> r = reported == null ? null : reported.get(capability);
        if (r == null) {
            return false;
        }
        return args.entrySet().stream().allMatch(e -> valuesEqual(e.getValue(), r.get(e.getKey())));
    }

    /** 두 상태 사이의 바뀐 속성 목록(EVT-ACT-02 {@code changed}). 순서: 기능·속성 이름 순서(이전 → 새) */
    public static List<StateChange> changes(Map<String, Map<String, Object>> before, Map<String, Map<String, Object>> after) {
        Map<String, Map<String, Object>> b = before == null ? Map.of() : before;
        Map<String, Map<String, Object>> a = after == null ? Map.of() : after;
        Set<String> capabilities = new LinkedHashSet<>(b.keySet());
        capabilities.addAll(a.keySet());
        List<StateChange> out = new ArrayList<>();
        for (String capability : capabilities) {
            Map<String, Object> from = b.getOrDefault(capability, Map.of());
            Map<String, Object> to = a.getOrDefault(capability, Map.of());
            Set<String> attrs = new LinkedHashSet<>(from.keySet());
            attrs.addAll(to.keySet());
            for (String attr : attrs) {
                if (!valuesEqual(from.get(attr), to.get(attr))) {
                    out.add(new StateChange(capability, attr, from.get(attr), to.get(attr)));
                }
            }
        }
        return List.copyOf(out);
    }

    /** 값 비교: 숫자는 크기로(24 == 24.0), 나머지는 equals */
    public static boolean valuesEqual(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return new BigDecimal(x.toString()).compareTo(new BigDecimal(y.toString())) == 0;
        }
        return Objects.equals(a, b);
    }

    private static Map<String, Map<String, Object>> mutableCopy(Map<String, Map<String, Object>> base) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        if (base != null) {
            base.forEach((k, v) -> out.put(k, new LinkedHashMap<>(v)));
        }
        return out;
    }

    private static Map<String, Map<String, Object>> freeze(Map<String, Map<String, Object>> map) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        map.forEach((k, v) -> out.put(k, Collections.unmodifiableMap(new LinkedHashMap<>(v))));
        return Collections.unmodifiableMap(out);
    }
}
