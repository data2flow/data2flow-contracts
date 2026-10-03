package net.java21.data2flow.contracts.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ING-01.01·ING-04.04·ING-05.01: 스트림 이름·라우팅 키·소비자 그룹·중복 키·client-id 규칙 */
class MessagingContractsTest {

    @Test
    @DisplayName("ING-01.01 Super Stream 정의는 architecture.md §4.2와 같다(12 파티션, 보관 7일/10GB·3일/5GB)")
    void superStreams() {
        assertThat(SuperStreamSpec.RAW.name()).isEqualTo("data2flow.raw");
        assertThat(SuperStreamSpec.RAW.partitions()).isEqualTo(12);
        assertThat(SuperStreamSpec.RAW.maxAge()).isEqualTo(Duration.ofDays(7));
        assertThat(SuperStreamSpec.RAW.maxBytesPerPartition()).isEqualTo(10L * 1024 * 1024 * 1024);
        assertThat(SuperStreamSpec.TELEMETRY.name()).isEqualTo("data2flow.telemetry");
        assertThat(SuperStreamSpec.TELEMETRY.maxAge()).isEqualTo(Duration.ofDays(3));
        assertThat(SuperStreamSpec.TELEMETRY.partition(0)).isEqualTo("data2flow.telemetry-0");
        assertThat(SuperStreamSpec.TELEMETRY.partition(11)).isEqualTo("data2flow.telemetry-11");
        assertThatThrownBy(() -> SuperStreamSpec.RAW.partition(12)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SuperStreamSpec.RAW.partition(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ING-05.01 TC-ING-063 라우팅 키: raw는 sha1(sourceId+topic) 16진수, telemetry는 deviceId")
    void routingKeys() {
        assertThat(StreamRoutingKeys.raw(3, "a/b")).isEqualTo(StreamRoutingKeys.raw(3, "a/b")).hasSize(40)
                .isNotEqualTo(StreamRoutingKeys.raw(4, "a/b")).isNotEqualTo(StreamRoutingKeys.raw(3, "a/c"));
        // sha1("3a/b")
        assertThat(StreamRoutingKeys.raw(3, "a/b")).isEqualTo("114bec10f680ac65535b1361d10126e589391c08");
        assertThat(StreamRoutingKeys.raw(3, null)).isEqualTo(StreamRoutingKeys.raw(3, ""));
        assertThat(StreamRoutingKeys.telemetry(17)).isEqualTo("17");
    }

    @Test
    @DisplayName("ING-01.01 소비자 그룹: 운영·staging은 그대로, 로컬 개발은 개발자 이름을 붙인다(deployment.md §8.2)")
    void consumerGroups() {
        assertThat(ConsumerGroups.of(ConsumerGroups.PIPELINE, null)).isEqualTo("pipeline");
        assertThat(ConsumerGroups.of(ConsumerGroups.PIPELINE, " ")).isEqualTo("pipeline");
        assertThat(ConsumerGroups.of(ConsumerGroups.PIPELINE, "nhn")).isEqualTo("pipeline-nhn");
        assertThat(ConsumerGroups.of(ConsumerGroups.CORE_LIVE, "kim2")).isEqualTo("core-live-kim2");
        assertThat(ConsumerGroups.FLOW).isEqualTo("flow");
        assertThat(ConsumerGroups.ANALYTICS).isEqualTo("analytics");
        assertThat(ConsumerGroups.PIPELINE_REPROCESS).isEqualTo("pipeline-reprocess");
        assertThatThrownBy(() -> ConsumerGroups.of(ConsumerGroups.FLOW, "Kim")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConsumerGroups.of(" ", "kim")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ING-04.04 TC-ING-058 중복 키: ChirpStack deduplicationId 우선, 아니면 내용 해시. 수신 인스턴스와 무관하다")
    void dedupKeys() {
        byte[] chirp = "{\"deduplicationId\":\"3F1E9B2A-0C4D-4E5F-8A6B-7C8D9E0F1A2B\",\"object\":{}}".getBytes(StandardCharsets.UTF_8);
        assertThat(DedupKeys.detect(3, "t", chirp)).isEqualTo("chirpstack:3f1e9b2a-0c4d-4e5f-8a6b-7c8d9e0f1a2b");
        byte[] plain = "{\"temperature\":22.3}".getBytes(StandardCharsets.UTF_8);
        String content = DedupKeys.detect(3, "t", plain);
        assertThat(content).startsWith("sha256:").hasSize(7 + 64).isEqualTo(DedupKeys.content(3, "t", plain));
        assertThat(DedupKeys.content(4, "t", plain)).isNotEqualTo(content);
        assertThat(DedupKeys.content(3, null, plain)).isEqualTo(DedupKeys.content(3, "", plain));
        assertThatThrownBy(() -> DedupKeys.chirpStack(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ING-04.04 BR-ING-07 값만 있는 payload는 보고 주기 절반 버킷 안의 재전송만 같은 키")
    void bucketedDedupKeys() {
        byte[] value = "22.3".getBytes(StandardCharsets.UTF_8);
        Duration half = Duration.ofSeconds(30);
        Instant t = Instant.parse("2026-10-03T00:00:05Z");
        String first = DedupKeys.contentInBucket(3, "t", value, t, half);
        assertThat(DedupKeys.contentInBucket(3, "t", value, t.plusSeconds(20), half)).isEqualTo(first);
        assertThat(DedupKeys.contentInBucket(3, "t", value, t.plusSeconds(60), half)).isNotEqualTo(first);
        assertThat(first).startsWith("sha256b:").hasSizeLessThanOrEqualTo(128);
        assertThatThrownBy(() -> DedupKeys.contentInBucket(3, "t", value, t, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DSC-01.04 BR-DSC-01 client-id: {base}-{env}-{n}, 개발자는 dev-{이름}, 테스트는 test-{난수}")
    void clientIds() {
        assertThat(ClientIds.of("data2flow-ingress", "prod", 1)).isEqualTo("data2flow-ingress-prod-1");
        assertThat(ClientIds.developer("data2flow-ingress", "nhn", 1)).isEqualTo("data2flow-ingress-dev-nhn-1");
        assertThat(ClientIds.test("data2flow-chirpstack-s3", "a1b2")).isEqualTo("data2flow-chirpstack-s3-test-a1b2");
        assertThatThrownBy(() -> ClientIds.of("Data2flow", "prod", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClientIds.of("d", "prod", -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClientIds.of("d".repeat(130), "prod", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ING-01.01 vhost·exchange·큐 이름(architecture.md §4, ADR-030)")
    void names() {
        assertThat(MessagingNames.VHOST_PROD).isEqualTo("data2flow");
        assertThat(MessagingNames.VHOST_STAGING).isEqualTo("data2flow-stg");
        assertThat(MessagingNames.VHOST_DEV).isEqualTo("data2flow-dev");
        assertThat(MessagingNames.eventsQueue("core")).isEqualTo("core.events");
        assertThat(MessagingNames.deadLetterQueue(MessagingNames.QUEUE_ACTION_COMMANDS)).isEqualTo("action.commands.dlq");
        assertThat(MessagingNames.EXCHANGE_DLX).isEqualTo("data2flow.dlx");
        assertThat(MessagingNames.DELIVERY_LIMIT).isEqualTo(5);
    }
}
