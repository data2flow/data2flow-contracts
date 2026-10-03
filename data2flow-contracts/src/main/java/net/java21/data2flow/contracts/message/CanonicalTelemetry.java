package net.java21.data2flow.contracts.message;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import net.java21.data2flow.contracts.messaging.StreamRoutingKeys;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 표준 텔레메트리 {@code CanonicalTelemetry} v1(EVT-ING-02, ING-02.01·ING-05.01). Super Stream {@code data2flow.telemetry}의 본문이다.
 *
 * <p>pipeline이 디코딩·스크립트·기기 식별·검증을 마친 뒤 DB 커밋에 성공한 메시지만 발행한다. flow-engine(그룹 {@code flow}),
 * analytics(그룹 {@code analytics}), core-api 실시간 화면(그룹 {@code core-live})이 읽는다. 파티션 라우팅 키는
 * {@link #routingKey()}({@code deviceId})라서 같은 기기는 같은 파티션에서 순서대로 처리된다(reliability-and-ha.md §2.2).
 *
 * <p>소비자는 모르는 필드를 무시한다. 필드 추가는 같은 버전 안에서 하고, 뜻이 바뀌는 변경만 {@code v}를 올린다.
 *
 * @param v             스키마 버전(1)
 * @param messageId     메시지 ID. 하위 중복 제거 키
 * @param organizationId 조직 ID
 * @param sourceId      데이터 소스 ID
 * @param externalId    기기 외부 ID(devEui 등)
 * @param deviceId      기기 ID
 * @param deviceStatus  기기 상태. INACTIVE는 규칙·플로우가 무시한다
 * @param modelId       기기 모델 ID(문자열). 없으면 null
 * @param spaceId       공간 ID. 없으면 null
 * @param measuredAt    측정 시각(보정 후, UTC)
 * @param receivedAt    수신 시각(UTC)
 * @param late          1시간 이상 늦게 도착(ING-06.03)
 * @param virtual       가상 데이터(SIM-07.01)
 * @param metrics       측정값(최대 {@value #MAX_METRICS}개)
 * @param link          통신 품질. 없으면 null
 * @param meta          태그·디코더·스크립트 등 부가 정보. 없으면 null
 * @param rawMessageId  원본 {@code raw_messages.id}(추적용)
 */
@MessageSchema(name = "canonical-telemetry", version = 1)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CanonicalTelemetry(
        int v,
        UUID messageId,
        long organizationId,
        long sourceId,
        String externalId,
        long deviceId,
        DeviceStatus deviceStatus,
        String modelId,
        Long spaceId,
        Instant measuredAt,
        Instant receivedAt,
        boolean late,
        boolean virtual,
        List<Metric> metrics,
        Link link,
        Meta meta,
        long rawMessageId) implements Message {

    public static final int VERSION = 1;
    /** 메시지당 측정 항목 상한(BR-ING-10) */
    public static final int MAX_METRICS = 100;
    /** 측정 키 길이 상한(BR-ING-10) */
    public static final int MAX_KEY_LENGTH = 64;

    public CanonicalTelemetry {
        Messages.requireVersion(v);
        Messages.require(messageId, "messageId");
        Messages.requireId(organizationId, "organizationId");
        Messages.requireId(sourceId, "sourceId");
        Messages.requireText(externalId, "externalId");
        Messages.requireId(deviceId, "deviceId");
        Messages.requireId(rawMessageId, "rawMessageId");
        Messages.require(deviceStatus, "deviceStatus");
        Messages.require(measuredAt, "measuredAt");
        Messages.require(receivedAt, "receivedAt");
        metrics = Messages.list(Messages.require(metrics, "metrics"));
        if (metrics.size() > MAX_METRICS) {
            throw new MessageFormatException("metrics는 " + MAX_METRICS + "개 이하여야 합니다: " + metrics.size());
        }
    }

    /** {@code data2flow.telemetry} 파티션 라우팅 키 */
    public String routingKey() {
        return StreamRoutingKeys.telemetry(deviceId);
    }

    /** 이 키의 측정값. 없으면 null */
    public Metric metric(String key) {
        return metrics.stream().filter(m -> m.key().equals(key)).findFirst().orElse(null);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 기기 상태(DEV {@code devices.status}) */
    public enum DeviceStatus {
        PENDING, ACTIVE, INACTIVE
    }

    /**
     * 측정값 하나.
     *
     * @param key     표준 측정 키(별칭 변환 후, 64자 이하)
     * @param value   값(유한한 숫자)
     * @param unit    측정 항목 정의의 단위. 없으면 null
     * @param quality 품질 코드 0~5({@link Quality})
     * @param derived 스크립트·수식이 만든 파생 항목이면 true, 아니면 null
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Metric(String key, Double value, String unit, int quality, Boolean derived) {

        public Metric {
            Messages.requireText(key, "metrics[].key");
            if (key.length() > MAX_KEY_LENGTH) {
                throw new MessageFormatException("metrics[].key는 " + MAX_KEY_LENGTH + "자 이하여야 합니다: " + key);
            }
            Messages.require(value, "metrics[].value");
            if (value.isNaN() || value.isInfinite()) {
                throw new MessageFormatException("metrics[].value는 유한한 숫자여야 합니다: " + key);
            }
            if (quality < Quality.NORMAL || quality > Quality.FORECAST) {
                throw new MessageFormatException("metrics[].quality는 0~5입니다: " + quality);
            }
        }

        public static Metric of(String key, double value, String unit) {
            return new Metric(key, value, unit, Quality.NORMAL, null);
        }

        public Metric withQuality(int newQuality) {
            return new Metric(key, value, unit, newQuality, derived);
        }
    }

    /**
     * 통신 품질(TSD-01.02).
     *
     * @param rssi         대표 수신 세기(dBm)
     * @param snr          대표 신호 대 잡음비(dB)
     * @param frameCounter LoRaWAN fCnt 등 프레임 카운터
     * @param gateways     받은 게이트웨이별 품질
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Link(Double rssi, Double snr, Long frameCounter, List<GatewayReception> gateways) {

        public Link {
            gateways = gateways == null ? null : List.copyOf(gateways);
        }
    }

    /** 게이트웨이 하나가 받은 품질 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GatewayReception(String eui, Double rssi, Double snr) {

        public GatewayReception {
            Messages.requireText(eui, "link.gateways[].eui");
        }
    }

    /**
     * 부가 정보. 알려진 필드 밖의 키(예: 하트비트 {@code heartbeat.stages[]}, EVT-ING-07)는 {@link #extra()}에 그대로 보존한다.
     *
     * @param tags    소스가 준 태그(ChirpStack tags 등). 공간 자동 매핑 제안에 쓴다(ING-03.03)
     * @param decoder 사용한 디코더
     * @param scripts 적용한 스크립트와 버전(재처리·추적용)
     * @param extra   그 밖의 키
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Meta(Map<String, String> tags, DecoderRef decoder, List<ScriptRef> scripts,
                       @JsonAnySetter Map<String, JsonNode> extra) {

        public Meta {
            tags = tags == null ? null : Map.copyOf(tags);
            scripts = scripts == null ? null : List.copyOf(scripts);
            extra = extra == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extra);
        }

        public Meta(Map<String, String> tags, DecoderRef decoder, List<ScriptRef> scripts) {
            this(tags, decoder, scripts, null);
        }

        @Override
        @JsonAnyGetter
        public Map<String, JsonNode> extra() {
            return Map.copyOf(extra);
        }
    }

    /** 디코더 키와 버전(예: {@code chirpstack-v4}, {@code script:42@v3}) */
    public record DecoderRef(String key, String version) {
    }

    /** 적용한 스크립트 ID와 버전 번호 */
    public record ScriptRef(long id, int version) {
    }

    /** {@link CanonicalTelemetry} 빌더. messageId를 정하지 않으면 새 UUID, v는 현재 버전 */
    public static final class Builder {
        private UUID messageId;
        private long organizationId;
        private long sourceId;
        private String externalId;
        private long deviceId;
        private DeviceStatus deviceStatus = DeviceStatus.ACTIVE;
        private String modelId;
        private Long spaceId;
        private Instant measuredAt;
        private Instant receivedAt;
        private boolean late;
        private boolean virtual;
        private final List<Metric> metrics = new ArrayList<>();
        private Link link;
        private Meta meta;
        private long rawMessageId;

        private Builder() {
        }

        public Builder messageId(UUID value) { this.messageId = value; return this; }
        public Builder organizationId(long value) { this.organizationId = value; return this; }
        public Builder sourceId(long value) { this.sourceId = value; return this; }
        public Builder externalId(String value) { this.externalId = value; return this; }
        public Builder deviceId(long value) { this.deviceId = value; return this; }
        public Builder deviceStatus(DeviceStatus value) { this.deviceStatus = value; return this; }
        public Builder modelId(String value) { this.modelId = value; return this; }
        public Builder spaceId(Long value) { this.spaceId = value; return this; }
        public Builder measuredAt(Instant value) { this.measuredAt = value; return this; }
        public Builder receivedAt(Instant value) { this.receivedAt = value; return this; }
        public Builder late(boolean value) { this.late = value; return this; }
        public Builder virtual(boolean value) { this.virtual = value; return this; }
        public Builder metric(Metric value) { this.metrics.add(value); return this; }
        public Builder metrics(List<Metric> values) { this.metrics.addAll(values); return this; }
        public Builder link(Link value) { this.link = value; return this; }
        public Builder meta(Meta value) { this.meta = value; return this; }
        public Builder rawMessageId(long value) { this.rawMessageId = value; return this; }

        public CanonicalTelemetry build() {
            return new CanonicalTelemetry(VERSION, messageId == null ? UUID.randomUUID() : messageId, organizationId,
                    sourceId, externalId, deviceId, deviceStatus, modelId, spaceId, measuredAt, receivedAt, late,
                    virtual, metrics, link, meta, rawMessageId);
        }
    }
}
