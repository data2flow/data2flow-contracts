package net.java21.data2flow.contracts.connector;

import net.java21.data2flow.contracts.message.RawEnvelope;
import net.java21.data2flow.contracts.message.SourceTypes;
import net.java21.data2flow.contracts.message.event.SourceRuntimeReported;
import net.java21.data2flow.contracts.secret.Secret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** DSC-09.02: 커넥터 SPI 값 타입(설명·상태·연결 테스트 결과·설정·실행 환경) */
class ConnectorSpiTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    private ConnectorDescriptor mqtt(AckMode ack) {
        return new ConnectorDescriptor("mqtt", "MQTT", "1.0.0", ConnectorCategory.MQTT,
                Set.of(AuthMethod.USER_PASSWORD, AuthMethod.WS_HEADER), Set.of(PayloadFormat.JSON), ack,
                ScalingMode.DUAL_ACTIVE, true);
    }

    @Test
    @DisplayName("DSC-09.02 TC-DSC-235 커넥터 설명은 키·semver·분류·인증·형식·확인·확장 방식을 모두 갖춰야 한다")
    void descriptorValidation() {
        assertThat(mqtt(AckMode.AFTER_WRITE).lossPossible()).isFalse();
        assertThat(mqtt(AckMode.CURSOR).lossPossible()).isFalse();
        assertThat(mqtt(AckMode.NONE).lossPossible()).isTrue();
        assertThat(mqtt(AckMode.AUTO).lossPossible()).isTrue();
        assertThatThrownBy(() -> new ConnectorDescriptor("MQTT", "x", "1.0.0", ConnectorCategory.MQTT, Set.of(AuthMethod.NONE),
                Set.of(PayloadFormat.JSON), AckMode.AUTO, ScalingMode.SCALABLE, false)).hasMessageContaining("키");
        assertThatThrownBy(() -> new ConnectorDescriptor("mqtt", "x", "1.0", ConnectorCategory.MQTT, Set.of(AuthMethod.NONE),
                Set.of(PayloadFormat.JSON), AckMode.AUTO, ScalingMode.SCALABLE, false)).hasMessageContaining("semver");
        assertThatThrownBy(() -> new ConnectorDescriptor("mqtt", " ", "1.0.0", ConnectorCategory.MQTT, Set.of(AuthMethod.NONE),
                Set.of(PayloadFormat.JSON), AckMode.AUTO, ScalingMode.SCALABLE, false)).hasMessageContaining("이름");
        assertThatThrownBy(() -> new ConnectorDescriptor("mqtt", "x", "1.0.0", ConnectorCategory.MQTT, Set.of(),
                Set.of(PayloadFormat.JSON), AckMode.AUTO, ScalingMode.SCALABLE, false)).hasMessageContaining("authMethods");
        assertThatThrownBy(() -> new ConnectorDescriptor("mqtt", "x", "1.0.0", null, Set.of(AuthMethod.NONE),
                Set.of(PayloadFormat.JSON), AckMode.AUTO, ScalingMode.SCALABLE, false)).hasMessageContaining("category");
        assertThat(new ConnectorDescriptor("sparkplug-b", "Sparkplug B", "2.1.0-rc.1", ConnectorCategory.MQTT,
                Set.of(AuthMethod.MTLS), Set.of(PayloadFormat.SPARKPLUG_B), AckMode.AFTER_WRITE, ScalingMode.SINGLETON,
                false).key()).isEqualTo("sparkplug-b");
    }

    @Test
    @DisplayName("DSC-09.02 EVT-DSC-09 카탈로그 항목은 설명과 설정 스키마를 합친다")
    void catalogEntry() {
        ConnectorCatalogEntry entry = ConnectorCatalogEntry.of(mqtt(AckMode.AFTER_WRITE), mapper.readTree("{\"type\":\"object\"}"));
        assertThat(entry.key()).isEqualTo("mqtt");
        assertThat(entry.scaling()).isEqualTo(ScalingMode.DUAL_ACTIVE);
        assertThat(entry.schema().get("type").asString()).isEqualTo("object");
        assertThat(new ConnectorCatalogEntry("k", "n", "1.0.0", ConnectorCategory.FILE, null, null, null, AckMode.CURSOR,
                ScalingMode.SINGLETON, false).authMethods()).isEmpty();
    }

    @Test
    @DisplayName("DSC-09.11 연결 테스트 결과: 처음 실패한 단계, 미리보기 최대 10건·원본 4KB")
    void connectionTestResult() {
        ConnectionTestResult ok = new ConnectionTestResult(List.of(
                ConnectionTestResult.Step.ok(ConnectionTestResult.STEP_DNS, 3),
                ConnectionTestResult.Step.ok(ConnectionTestResult.STEP_TCP, 10),
                ConnectionTestResult.Step.skipped(ConnectionTestResult.STEP_TLS)), null, false);
        assertThat(ok.succeeded()).isTrue();
        ConnectionTestResult failed = new ConnectionTestResult(List.of(
                ConnectionTestResult.Step.ok(ConnectionTestResult.STEP_DNS, 3),
                ConnectionTestResult.Step.failed(ConnectionTestResult.STEP_TLS, 40, "TLS_CERT_CHAIN", "unknown CA"),
                ConnectionTestResult.Step.failed(ConnectionTestResult.STEP_AUTH, 1, "AUTH", "x")), null, false);
        assertThat(failed.succeeded()).isFalse();
        assertThat(failed.failedStep()).get().extracting(ConnectionTestResult.Step::name).isEqualTo("TLS");
        assertThat(new ConnectionTestResult(null, null, true).succeeded()).isFalse();
        List<ConnectionTestResult.Preview> previews = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            previews.add(new ConnectionTestResult.Preview(Instant.EPOCH, "t", 5000, "x".repeat(5000), null));
        }
        ConnectionTestResult many = new ConnectionTestResult(List.of(), previews, false);
        assertThat(many.preview()).hasSize(ConnectionTestResult.MAX_PREVIEW);
        assertThat(many.preview().getFirst().rawExcerpt()).hasSize(4096);
        String json = mapper.writeValueAsString(failed);
        assertThat(json).contains("\"name\":\"TLS\"", "\"code\":\"TLS_CERT_CHAIN\"", "\"lossPossible\":false")
                .doesNotContain("succeeded", "tlsChain");
    }

    @Test
    @DisplayName("DSC-02.04 EVT-DSC-02 상태: ERROR는 원인 종류가 필요하고 설명은 500자로 자른다")
    void status() {
        assertThatThrownBy(() -> new ConnectorStatus(ConnectorState.ERROR, null, null, null, null, 0, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConnectorStatus(null, null, null, null, null, 0, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        ConnectorStatus error = ConnectorStatus.error(ConnectionErrorKind.TLS, "x".repeat(600));
        assertThat(error.errorMessage()).hasSize(500);
        SourceRuntimeReported report = error.toReport(3, "data2flow-ingress-0");
        assertThat(report.state()).isEqualTo(ConnectorState.ERROR);
        assertThat(report.errorKind()).isEqualTo(ConnectionErrorKind.TLS);
        assertThat(ConnectorStatus.of(ConnectorState.CONNECTING).received()).isZero();
    }

    @Test
    @DisplayName("NFR-03.02 BR-DSC-02 소스 설정의 비밀값은 출력해도 가려진다")
    void sourceConfigMasksSecrets() {
        SourceConfig cfg = new SourceConfig(1, 3, SourceTypes.MQTT_SUBSCRIBE, "mqtt", mapper.readTree("{}"),
                Map.of("basic", Secret.of("dXNlcjpwYXNz")), "data2flow-ingress-prod-0");
        assertThat(cfg.toString()).doesNotContain("dXNlcjpwYXNz").contains("***");
        assertThat(cfg.secret("basic").reveal()).isEqualTo("dXNlcjpwYXNz");
        assertThat(cfg.secret("none")).isNull();
        assertThat(new SourceConfig(1, 3, "WEBHOOK", "webhook", mapper.readTree("{}"), null, null).secrets()).isEmpty();
        assertThatThrownBy(() -> new SourceConfig(0, 3, "WEBHOOK", "webhook", mapper.readTree("{}"), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SourceConfig(1, 3, null, "webhook", mapper.readTree("{}"), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DSC-09.02 TC-DSC-237 실행 환경이 원본 봉투를 만든다: 수신 시각은 시계, 인스턴스 이름, 중복 키 자동 판별")
    void contextBuildsEnvelope() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-03T02:40:09Z"), ZoneOffset.UTC);
        List<ConnectorStatus> reported = new ArrayList<>();
        ConnectorContext ctx = new ConnectorContext("data2flow-ingress-1", clock, reported::add);
        SourceConfig cfg = new SourceConfig(1, 3, SourceTypes.MQTT_SUBSCRIBE, "mqtt", mapper.readTree("{}"), null, null);
        RawEnvelope raw = ctx.envelope(cfg, "application/1/device/x/event/up",
                "{\"deduplicationId\":\"0b5c1d2e-aaaa-bbbb-cccc-000000000001\"}".getBytes(StandardCharsets.UTF_8));
        assertThat(raw.receivedAt()).isEqualTo(clock.instant());
        assertThat(raw.ingressInstance()).isEqualTo("data2flow-ingress-1");
        assertThat(raw.dedupKey()).isEqualTo("chirpstack:0b5c1d2e-aaaa-bbbb-cccc-000000000001");
        assertThat(raw.organizationId()).isEqualTo(1);
        assertThat(raw.sourceType()).isEqualTo(SourceTypes.MQTT_SUBSCRIBE);
        ctx.reportStatus(ConnectorStatus.of(ConnectorState.CONNECTED));
        assertThat(reported).singleElement().extracting(ConnectorStatus::state).isEqualTo(ConnectorState.CONNECTED);
        new ConnectorContext("i", clock, null).reportStatus(ConnectorStatus.of(ConnectorState.DISABLED));
        assertThatThrownBy(() -> new ConnectorContext(" ", clock, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
