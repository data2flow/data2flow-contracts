package net.java21.data2flow.contracts.capability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** ACT-02.04: desired/reported/delta 상태 쌍(BR-ACT-03·04·05) */
class DeviceShadowTest {

    private static final Instant T = Instant.parse("2026-07-15T05:00:00Z");

    @Test
    @DisplayName("ACT-02.04 TC-ACT-046 BR-ACT-03 명령은 목표 상태 설정이라 같은 명령을 여러 번 적용해도 desired가 같다")
    void desiredIsIdempotent() {
        DeviceShadow once = DeviceShadow.EMPTY.withDesired("Thermostat", Map.of("mode", "cool", "targetTemperature", 24));
        DeviceShadow twice = once.withDesired("Thermostat", Map.of("mode", "cool", "targetTemperature", 24));
        assertThat(twice.desired()).isEqualTo(once.desired());
        assertThat(twice.desiredVersion()).isEqualTo(2);
        assertThat(once.desired()).isEqualTo(Map.of("Thermostat", Map.of("mode", "cool", "targetTemperature", 24)));
        assertThat(once.withDesired("Switch", Map.of("on", true)).desired()).containsOnlyKeys("Thermostat", "Switch");
    }

    @Test
    @DisplayName("ACT-02.04 TC-ACT-047 BR-ACT-05 보고는 버전이 클 때만 반영하고 오래된·같은 버전 보고는 버린다")
    void reportOrdering() {
        DeviceShadow s = DeviceShadow.EMPTY.withReported(8, Map.of("Thermostat", Map.of("mode", "cool")), T).orElseThrow();
        assertThat(s.reportedVersion()).isEqualTo(8);
        assertThat(s.reportedAt()).isEqualTo(T);
        assertThat(s.withReported(7, Map.of("Thermostat", Map.of("mode", "off")), T.plusSeconds(1))).isEmpty();   // TC-ACT-045
        assertThat(s.withReported(8, Map.of("Thermostat", Map.of("mode", "off")), T.plusSeconds(1))).isEmpty();
        DeviceShadow next = s.withReported(9, Map.of("Thermostat", Map.of("targetTemperature", 24)), T.plusSeconds(2)).orElseThrow();
        assertThat(next.reported().get("Thermostat")).containsEntry("mode", "cool").containsEntry("targetTemperature", 24);
    }

    @Test
    @DisplayName("ACT-02.04 TC-ACT-041 desired on=true(v5)에 이전 보고가 오면 버리고 delta가 남는다, 반영되면 delta가 사라진다")
    void deltaAndApplied() {
        DeviceShadow s = new DeviceShadow(Map.of("Switch", Map.of("on", true)), 5, Map.of("Switch", Map.of("on", false)), 5, T);
        assertThat(s.delta()).isEqualTo(Map.of("Switch", Map.of("on", true)));
        assertThat(s.inSync()).isFalse();
        assertThat(s.withReported(4, Map.of("Switch", Map.of("on", false)), T)).isEmpty();
        DeviceShadow applied = s.withReported(6, Map.of("Switch", Map.of("on", true)), T.plusSeconds(3)).orElseThrow();
        assertThat(applied.delta()).isEmpty();
        assertThat(applied.inSync()).isTrue();
        assertThat(applied.isApplied("Switch", Map.of("on", true))).isTrue();
        assertThat(applied.isApplied("Thermostat", Map.of("mode", "cool"))).isFalse();
        assertThat(applied.isApplied("Thermostat", Map.of())).isTrue();
    }

    @Test
    @DisplayName("ACT-02.04 숫자는 크기로 비교한다(24 == 24.0): 가상 장비가 24.0으로 보고해도 APPLIED")
    void numericEquality() {
        DeviceShadow s = DeviceShadow.EMPTY.withDesired("Thermostat", Map.of("targetTemperature", 24))
                .withReported(1, Map.of("Thermostat", Map.of("targetTemperature", 24.0)), T).orElseThrow();
        assertThat(s.delta()).isEmpty();
        assertThat(s.isApplied("Thermostat", Map.of("targetTemperature", 24L))).isTrue();
        assertThat(CapabilityStates.valuesEqual(24, 24.5)).isFalse();
        assertThat(CapabilityStates.valuesEqual("a", "a")).isTrue();
        assertThat(CapabilityStates.valuesEqual(null, null)).isTrue();
        assertThat(CapabilityStates.valuesEqual(1, "1")).isFalse();
    }

    @Test
    @DisplayName("ACT-05.03 BR-ACT-04 desired·reported 모두 목표와 같으면 바뀌는 것이 없다(SKIPPED(NO_CHANGE))")
    void noChange() {
        DeviceShadow s = new DeviceShadow(Map.of("Switch", Map.of("on", true)), 1, Map.of("Switch", Map.of("on", true)), 1, T);
        assertThat(s.noChange("Switch", Map.of("on", true))).isTrue();
        assertThat(s.noChange("Switch", Map.of("on", false))).isFalse();
        DeviceShadow pending = new DeviceShadow(Map.of("Switch", Map.of("on", true)), 1, Map.of("Switch", Map.of("on", false)), 1, T);
        assertThat(pending.noChange("Switch", Map.of("on", true))).isFalse();
    }

    @Test
    @DisplayName("ACT-02.04 EVT-ACT-02 changed: 바뀐 속성만 이전 → 새 값으로")
    void changes() {
        Map<String, Map<String, Object>> before = Map.of("Thermostat", Map.of("mode", "off", "targetTemperature", 26));
        Map<String, Map<String, Object>> after = CapabilityStates.mergeAll(before,
                Map.of("Thermostat", Map.of("mode", "cool", "targetTemperature", 26.0), "Switch", Map.of("on", true)));
        assertThat(CapabilityStates.changes(before, after)).containsExactlyInAnyOrder(
                new StateChange("Thermostat", "mode", "off", "cool"),
                new StateChange("Switch", "on", null, true));
        assertThat(CapabilityStates.changes(null, null)).isEmpty();
        assertThat(CapabilityStates.delta(null, after)).isEmpty();
        assertThat(CapabilityStates.delta(after, null)).isEqualTo(after);
        assertThat(CapabilityStates.isApplied(null, "Switch", Map.of("on", true))).isFalse();
        assertThat(CapabilityStates.empty()).isEmpty();
        assertThat(CapabilityStates.merge(null, "Switch", null)).isEqualTo(Map.of("Switch", Map.of()));
        java.util.Map<String, Object> withNull = new java.util.HashMap<>();
        withNull.put("on", null);
        assertThat(CapabilityStates.merge(Map.of(), "Switch", withNull).get("Switch")).isEmpty();
        assertThat(new DeviceShadow(null, 0, null, 0, null)).isEqualTo(DeviceShadow.EMPTY);
    }
}
