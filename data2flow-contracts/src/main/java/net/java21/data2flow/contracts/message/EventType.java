package net.java21.data2flow.contracts.message;

import net.java21.data2flow.contracts.command.CommandStatus;
import net.java21.data2flow.contracts.message.event.AggregatesRecomputed;
import net.java21.data2flow.contracts.message.event.ClockSkewSuspected;
import net.java21.data2flow.contracts.message.event.CommandStatusChanged;
import net.java21.data2flow.contracts.message.event.ConnectorCatalogReported;
import net.java21.data2flow.contracts.message.event.DeviceChanged;
import net.java21.data2flow.contracts.message.event.DeviceCommandAck;
import net.java21.data2flow.contracts.message.event.DeviceConnectivityChanged;
import net.java21.data2flow.contracts.message.event.DevicePendingCreated;
import net.java21.data2flow.contracts.message.event.DeviceStateChanged;
import net.java21.data2flow.contracts.message.event.DeviceStateReported;
import net.java21.data2flow.contracts.message.event.EventPayload;
import net.java21.data2flow.contracts.message.event.FlowApplyReported;
import net.java21.data2flow.contracts.message.event.FlowStateChanged;
import net.java21.data2flow.contracts.message.event.GroupMembershipChanged;
import net.java21.data2flow.contracts.message.event.IngestAlert;
import net.java21.data2flow.contracts.message.event.IngestGapDetected;
import net.java21.data2flow.contracts.message.event.MetricUnverifiedRegistered;
import net.java21.data2flow.contracts.message.event.PartitionWarning;
import net.java21.data2flow.contracts.message.event.SimDataPurged;
import net.java21.data2flow.contracts.message.event.SimFaultLabel;
import net.java21.data2flow.contracts.message.event.SimRunChanged;
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
 * <p>M2(수집 경로)와 M3(가상 폐루프: ACT·SIM·FLW)에서 쓰는 종류가 있다. 다음 마일스톤이 종류를 더할 때는 여기에 상수를 추가하고
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
    PARTITION_WARNING("partition.warning", "EVT-TSD-06", PartitionWarning.class),

    COMMAND_STATUS_REQUESTED("command.status.requested", "EVT-ACT-01", CommandStatusChanged.class),
    COMMAND_STATUS_REJECTED("command.status.rejected", "EVT-ACT-01", CommandStatusChanged.class),
    COMMAND_STATUS_BLOCKED("command.status.blocked", "EVT-ACT-01", CommandStatusChanged.class),
    COMMAND_STATUS_SKIPPED("command.status.skipped", "EVT-ACT-01", CommandStatusChanged.class),
    COMMAND_STATUS_DELAYED("command.status.delayed", "EVT-ACT-01", CommandStatusChanged.class),
    COMMAND_STATUS_QUEUED("command.status.queued", "EVT-ACT-01", CommandStatusChanged.class),
    COMMAND_STATUS_QUEUED_FOR_DOWNLINK("command.status.queued_for_downlink", "EVT-ACT-01", CommandStatusChanged.class),
    COMMAND_STATUS_SENT("command.status.sent", "EVT-ACT-01", CommandStatusChanged.class),
    COMMAND_STATUS_ACKED("command.status.acked", "EVT-ACT-01", CommandStatusChanged.class),
    COMMAND_STATUS_APPLIED("command.status.applied", "EVT-ACT-01", CommandStatusChanged.class),
    COMMAND_STATUS_TIMEOUT("command.status.timeout", "EVT-ACT-01", CommandStatusChanged.class),
    COMMAND_STATUS_FAILED("command.status.failed", "EVT-ACT-01", CommandStatusChanged.class),
    COMMAND_STATUS_SUPERSEDED("command.status.superseded", "EVT-ACT-01", CommandStatusChanged.class),
    COMMAND_STATUS_CANCELLED("command.status.cancelled", "EVT-ACT-01", CommandStatusChanged.class),
    DEVICE_STATE_CHANGED("device.state.changed", "EVT-ACT-02", DeviceStateChanged.class),
    DEVICE_COMMAND_ACK("device.command.ack", "EVT-ACT-06", DeviceCommandAck.class),
    DEVICE_STATE_REPORTED("device.state.reported", "EVT-ACT-07", DeviceStateReported.class),

    SIM_RUN_STARTED("sim.run.started", "EVT-SIM-01", SimRunChanged.class),
    SIM_RUN_PAUSED("sim.run.paused", "EVT-SIM-01", SimRunChanged.class),
    SIM_RUN_RESUMED("sim.run.resumed", "EVT-SIM-01", SimRunChanged.class),
    SIM_RUN_STOPPED("sim.run.stopped", "EVT-SIM-01", SimRunChanged.class),
    SIM_RUN_COMPLETED("sim.run.completed", "EVT-SIM-01", SimRunChanged.class),
    SIM_RUN_FAILED("sim.run.failed", "EVT-SIM-01", SimRunChanged.class),
    SIM_RUN_THROTTLED("sim.run.throttled", "EVT-SIM-01", SimRunChanged.class),
    SIM_FAULT_STARTED("sim.fault.started", "EVT-SIM-02", SimFaultLabel.class),
    SIM_FAULT_ENDED("sim.fault.ended", "EVT-SIM-02", SimFaultLabel.class),
    SIM_DATA_PURGED("sim.data.purged", "EVT-SIM-04", SimDataPurged.class),

    FLOW_APPLY_REPORTED("flow.apply.reported", "EVT-FLW-02", FlowApplyReported.class),
    FLOW_STATE_CHANGED("flow.state.changed", "EVT-FLW-03", FlowStateChanged.class);

    /** EVT-SIM-01 라우팅 키 접두사 */
    public static final String SIM_RUN_PREFIX = "sim.run.";

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

    /** 이 종류의 현재 스키마 버전. 모든 M2·M3 이벤트는 1 */
    public int version() {
        return DomainEvent.VERSION;
    }

    /** EVT-ACT-01 상태별 종류. UNKNOWN이면 {@link IllegalArgumentException} */
    public static EventType commandStatus(CommandStatus status) {
        return fromRoutingKey(status == CommandStatus.UNKNOWN ? "" : status.routingKey())
                .orElseThrow(() -> new IllegalArgumentException("발행할 수 없는 명령 상태입니다: " + status));
    }

    /**
     * EVT-SIM-01 실행 상태 이벤트 종류. {@code event}는 라우팅 키 끝(started, paused, resumed, stopped, completed, failed, throttled)
     */
    public static EventType simRun(String event) {
        return fromRoutingKey(SIM_RUN_PREFIX + event)
                .orElseThrow(() -> new IllegalArgumentException("모르는 실행 상태 이벤트입니다: " + event));
    }

    /** 라우팅 키로 찾는다. 이 코드가 모르는 종류면 빈 값(소비자는 무시한다) */
    public static Optional<EventType> fromRoutingKey(String routingKey) {
        return Optional.ofNullable(BY_ROUTING_KEY.get(routingKey));
    }
}
