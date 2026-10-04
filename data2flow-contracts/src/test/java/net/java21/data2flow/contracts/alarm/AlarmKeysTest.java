package net.java21.data2flow.contracts.alarm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RUL-02.03 BR-RUL-02: 같은 키에는 열린 알람 하나. 생산자와 알람 서비스가 같은 키 규칙을 쓴다 */
class AlarmKeysTest {

    @Test
    @DisplayName("RUL-02.03 TC-RUL-052 알람 키: rule·flow·system 형식, 대상 키, 200자 제한, 출처 판별")
    void keys() {
        assertThat(AlarmKeys.rule(12, "15")).isEqualTo("rule:12:15");
        assertThat(AlarmKeys.target(null, 31L)).isEqualTo("space-31");
        assertThat(AlarmKeys.system("DRIVER_CIRCUIT_OPEN", "7")).isEqualTo("system:DRIVER_CIRCUIT_OPEN:7");
        assertThat(AlarmKeys.sourceOf("rule:1:2")).isEqualTo(AlarmSourceType.RULE);
        assertThat(AlarmKeys.sourceOf("flow:f:n:1")).isEqualTo(AlarmSourceType.FLOW);
        assertThat(AlarmKeys.sourceOf("system:X:1")).isEqualTo(AlarmSourceType.SYSTEM);
        assertThat(AlarmKeys.sourceOf("other")).isEqualTo(AlarmSourceType.UNKNOWN);
        assertThat(AlarmKeys.sourceOf(null)).isEqualTo(AlarmSourceType.UNKNOWN);
        assertThatThrownBy(() -> AlarmKeys.target(null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AlarmKeys.system("X")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AlarmKeys.flow("f", " ", "1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AlarmKeys.rule(1, "x".repeat(200))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("RUL-03.02 BR-RUL-12·14 심각도 순서: 최소 심각도 이상 판정, UNKNOWN은 어떤 기준도 넘지 않는다")
    void severityOrder() {
        assertThat(AlarmSeverity.CRITICAL.atLeast(AlarmSeverity.MAJOR)).isTrue();
        assertThat(AlarmSeverity.MAJOR.atLeast(AlarmSeverity.MAJOR)).isTrue();
        assertThat(AlarmSeverity.MINOR.atLeast(AlarmSeverity.MAJOR)).isFalse();
        assertThat(AlarmSeverity.UNKNOWN.atLeast(AlarmSeverity.INFO)).isFalse();
        assertThat(AlarmSeverity.INFO.atLeast(null)).isFalse();
        assertThat(AlarmSeverity.INFO.atLeast(AlarmSeverity.UNKNOWN)).isFalse();
    }
}
