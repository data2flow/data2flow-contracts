package net.java21.data2flow.contracts.message;

import net.java21.data2flow.contracts.message.event.AggregatesRecomputed;
import net.java21.data2flow.contracts.message.event.ClockSkewSuspected;
import net.java21.data2flow.contracts.message.event.ConnectorCatalogReported;
import net.java21.data2flow.contracts.message.event.DeviceChanged;
import net.java21.data2flow.contracts.message.event.DeviceConnectivityChanged;
import net.java21.data2flow.contracts.message.event.DevicePendingCreated;
import net.java21.data2flow.contracts.message.event.EventPayload;
import net.java21.data2flow.contracts.message.event.GroupMembershipChanged;
import net.java21.data2flow.contracts.message.event.IngestAlert;
import net.java21.data2flow.contracts.message.event.IngestGapDetected;
import net.java21.data2flow.contracts.message.event.MetricUnverifiedRegistered;
import net.java21.data2flow.contracts.message.event.PartitionWarning;
import net.java21.data2flow.contracts.message.event.SourceConnectionChanged;
import net.java21.data2flow.contracts.message.event.SourceDataActivity;
import net.java21.data2flow.contracts.message.event.SourceRuntimeReported;
import net.java21.data2flow.contracts.message.event.SourceStatsReported;
import net.java21.data2flow.contracts.message.event.SpaceChanged;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 도메인 이벤트 종류: {@code data2flow.events}(topic) 라우팅 키, 이벤트 ID, 페이로드 타입, 스키마 버전(architecture.md §4.5).
 *
 * <p>M2(수집 경로)에서 쓰는 종류만 있다. 다음 마일스톤이 종류를 더할 때는 여기에 상수를 추가하고
 * {@code domain-event.v1.json}의 {@code $defs}에도 페이로드 스키마를 더한다(계약 테스트가 둘이 맞는지 확인한다).
 */
public enum EventType {

    DEVICE_CHANGED("device.changed", "EVT-DEV-01", DeviceChanged.class),
    DEVICE_CONNECTIVITY_CHANGED("device.connectivity.changed", "EVT-DEV-02", DeviceConnectivityChanged.class),
    DEVICE_PENDING_CREATED("device.pending.created", "EVT-DEV-03", DevicePendingCreated.class),
    SPACE_CHANGED("space.changed", "EVT-DEV-05", SpaceChanged.class),
    GROUP_MEMBERSHIP_CHANGED("group.membership.changed", "EVT-DEV-07", GroupMembershipChanged.class),

    SOURCE_RUNTIME_REPORTED("source.runtime.reported", "EVT-DSC-02", SourceRuntimeReported.class),
    SOURCE_STATS_1M("source.stats.1m", "EVT-DSC-03", SourceStatsReported.class),
    SOURCE_CONNECTION_CHANGED("source.connection.changed", "EVT-DSC-04", SourceConnectionChanged.class),
    SOURCE_NO_DATA("source.no-data", "EVT-DSC-05", SourceDataActivity.class),
    SOURCE_DATA_RESUMED("source.data-resumed", "EVT-DSC-05", SourceDataActivity.class),
    CONNECTOR_CATALOG_REPORTED("connector.catalog.reported", "EVT-DSC-09", ConnectorCatalogReported.class),

    METRIC_UNVERIFIED_REGISTERED("metric.unverified.registered", "EVT-ING-04", MetricUnverifiedRegistered.class),
    INGEST_ALERT_RAISED("ingest.alert.raised", "EVT-ING-05", IngestAlert.class),
    INGEST_ALERT_CLEARED("ingest.alert.cleared", "EVT-ING-05", IngestAlert.class),
    INGEST_GAP_DETECTED("ingest.gap.detected", "EVT-ING-06", IngestGapDetected.class),
    INGEST_CLOCK_SKEW_SUSPECTED("ingest.clock-skew.suspected", "EVT-ING-06", ClockSkewSuspected.class),

    AGGREGATES_RECOMPUTED("aggregates.recomputed", "EVT-TSD-03", AggregatesRecomputed.class),
    PARTITION_WARNING("partition.warning", "EVT-TSD-06", PartitionWarning.class);

    private static final Map<String, EventType> BY_ROUTING_KEY = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(EventType::routingKey, Function.identity()));

    private final String routingKey;
    private final String eventId;
    private final Class<? extends EventPayload> payloadType;

    EventType(String routingKey, String eventId, Class<? extends EventPayload> payloadType) {
        this.routingKey = routingKey;
        this.eventId = eventId;
        this.payloadType = payloadType;
    }

    /** {@code data2flow.events} 라우팅 키이자 봉투의 {@code type} */
    public String routingKey() {
        return routingKey;
    }

    /** API 문서의 이벤트 ID(예: EVT-DEV-01) */
    public String eventId() {
        return eventId;
    }

    public Class<? extends EventPayload> payloadType() {
        return payloadType;
    }

    /** 이 종류의 현재 스키마 버전. 모든 M2 이벤트는 1 */
    public int version() {
        return DomainEvent.VERSION;
    }

    /** 라우팅 키로 찾는다. 이 코드가 모르는 종류면 빈 값(소비자는 무시한다) */
    public static Optional<EventType> fromRoutingKey(String routingKey) {
        return Optional.ofNullable(BY_ROUTING_KEY.get(routingKey));
    }
}
