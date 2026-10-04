package net.java21.data2flow.contracts.output;

import net.java21.data2flow.contracts.message.CanonicalTelemetry;
import net.java21.data2flow.contracts.messaging.ConsumerGroups;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** DSC-04.01 출력 연결(외부 MQTT·Webhook으로 표준 텔레메트리 전달): 필터·토픽 템플릿·별도 소비자 그룹 */
class OutputConnectionTest {

    private static final Instant T = Instant.parse("2026-10-03T02:40:09Z");

    private static CanonicalTelemetry telemetry() {
        return CanonicalTelemetry.builder().organizationId(1).sourceId(3).externalId("24e124").deviceId(17).spaceId(31L)
                .measuredAt(T).receivedAt(T).rawMessageId(1)
                .metric(CanonicalTelemetry.Metric.of("co2", 812, "ppm"))
                .metric(new CanonicalTelemetry.Metric("temperature", 24.1, "°C", 0, null))
                .metric(new CanonicalTelemetry.Metric("tvoc", 0.3, null, 2, null))
                .build();
    }

    @Test
    @DisplayName("DSC-04.01 TC-DSC-126 AT-DSC-10.1 필터에 co2만 넣으면 co2만 전달하고, 대상이 아니면 보내지 않는다")
    void filterSelectsMetrics() {
        OutputFilter co2 = new OutputFilter(List.of(17L), null, null, List.of("co2"), null);
        CanonicalTelemetry src = telemetry();
        CanonicalTelemetry out = co2.select(src, Set.of(), List.of()).orElseThrow();
        assertThat(out.metrics()).extracting(CanonicalTelemetry.Metric::key).containsExactly("co2");
        assertThat(out.messageId()).isEqualTo(src.messageId());
        assertThat(new OutputFilter(List.of(18L), null, null, null, null).select(telemetry(), Set.of(), List.of())).isEmpty();
        assertThat(new OutputFilter(null, List.of(5L), null, null, null).select(telemetry(), Set.of(5L), List.of())).isPresent();
        assertThat(new OutputFilter(null, List.of(5L), null, null, null).select(telemetry(), null, null)).isEmpty();
        assertThat(new OutputFilter(null, null, List.of(7L), null, null).select(telemetry(), Set.of(), List.of(1L, 7L, 31L)))
                .isPresent();
        assertThat(new OutputFilter(null, null, null, List.of("humidity"), null).select(telemetry(), Set.of(), List.of()))
                .isEmpty();
        CanonicalTelemetry normalOnly = new OutputFilter(null, null, null, null, 0).select(telemetry(), Set.of(), List.of())
                .orElseThrow();
        assertThat(normalOnly.metrics()).extracting(CanonicalTelemetry.Metric::key).containsExactly("co2", "temperature");
        CanonicalTelemetry all = telemetry();
        assertThat(OutputFilter.all().select(all, Set.of(), List.of())).containsSame(all);
        assertThatThrownBy(() -> new OutputFilter(null, null, null, null, 6)).isInstanceOf(IllegalArgumentException.class);
        assertThat(ConsumerGroups.of(ConsumerGroups.ACTION_OUTPUT, "nhn")).isEqualTo("action-output-nhn");
    }

    @Test
    @DisplayName("DSC-04.01 TC-DSC-130 토픽 템플릿: 허용 변수({spaceCode}·{deviceName}·{deviceId}·{metric}) 밖은 거부하고, 값의 /·+·#는 _로")
    void topicTemplate() {
        OutputTopicTemplate t = OutputTopicTemplate.of("d2f/{spaceCode}/{deviceName}/{metric}");
        assertThat(t.perMetric()).isTrue();
        assertThat(t.variables()).containsExactly("spaceCode", "deviceName", "metric");
        assertThat(t.render(Map.of("spaceCode", "B1-301", "deviceName", "센서 #1/좌", "metric", "co2")))
                .isEqualTo("d2f/B1-301/센서__1_좌/co2");
        assertThat(t.render(Map.of())).isEqualTo("d2f/_/_/_");
        assertThat(t.render(null)).isEqualTo("d2f/_/_/_");
        assertThat(t.toString()).isEqualTo(t.template());
        assertThat(OutputTopicTemplate.of("d2f/{deviceId}").perMetric()).isFalse();
        assertThat(OutputTopicTemplate.unknownVariables("d2f/{room}/{metric}")).containsExactly("room");
        assertThatThrownBy(() -> OutputTopicTemplate.of("d2f/{room}")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("room");
        assertThatThrownBy(() -> OutputTopicTemplate.of("d2f/+/{metric}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutputTopicTemplate.of("$SYS/{metric}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutputTopicTemplate.of(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutputTopicTemplate.of("{metric}").render(Map.of("metric", "x".repeat(300))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(OutputFailureKind.valueOf("HTTP_STATUS")).isNotNull();
        assertThat(OutputConnectionType.valueOf("WEBHOOK")).isNotNull();
        assertThat(OutputFormat.valueOf("CANONICAL")).isNotNull();
    }
}
