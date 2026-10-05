package net.java21.data2flow.contracts.message;

import net.java21.data2flow.contracts.command.CommandStatus;
import net.java21.data2flow.contracts.message.event.AggregatesRecomputed;
import net.java21.data2flow.contracts.message.event.AiQuotaExceeded;
import net.java21.data2flow.contracts.message.event.AnalyticsAnomalyDetected;
import net.java21.data2flow.contracts.message.event.AnalyticsEtaUpdated;
import net.java21.data2flow.contracts.message.event.AnalyticsExportCompleted;
import net.java21.data2flow.contracts.message.event.AnalyticsModelDrift;
import net.java21.data2flow.contracts.message.event.AnalyticsRunStatusChanged;
import net.java21.data2flow.contracts.message.event.AnalyticsScheduleStopped;
import net.java21.data2flow.contracts.message.event.AlarmSignal;
import net.java21.data2flow.contracts.message.event.AlarmStateChanged;
import net.java21.data2flow.contracts.message.event.NotificationDeliveryResult;
import net.java21.data2flow.contracts.message.event.EmergencyStopChanged;
import net.java21.data2flow.contracts.message.event.CommandNoEffect;
import net.java21.data2flow.contracts.message.event.DriverCircuitChanged;
import net.java21.data2flow.contracts.message.event.OscillationBlocked;
import net.java21.data2flow.contracts.message.event.FlowPromoted;
import net.java21.data2flow.contracts.message.event.LoRaWanDownlinkAck;
import net.java21.data2flow.contracts.message.event.MaintenanceChanged;
import net.java21.data2flow.contracts.message.event.OpsAlarmChanged;
import net.java21.data2flow.contracts.message.event.GatewayConnectivityChanged;
import net.java21.data2flow.contracts.message.event.CalendarSynced;
import net.java21.data2flow.contracts.message.event.SourceRotationProgress;
import net.java21.data2flow.contracts.message.event.CredentialRevoked;
import net.java21.data2flow.contracts.message.event.EdgeEvent;
import net.java21.data2flow.contracts.message.event.ExportJobFinished;
import net.java21.data2flow.contracts.message.event.ImportCompleted;
import net.java21.data2flow.contracts.message.event.RetentionPurged;
import net.java21.data2flow.contracts.message.event.BiExportFinished;
import net.java21.data2flow.contracts.message.event.WorkOrderChanged;
import net.java21.data2flow.contracts.message.event.SpaceModeChanged;
import net.java21.data2flow.contracts.message.event.DeviceExportCompleted;
import net.java21.data2flow.contracts.message.event.DeviceCommissioningChanged;
import net.java21.data2flow.contracts.message.event.ReprocessJobFinished;
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
 * <p>M2(수집 경로), M3(가상 폐루프: ACT·SIM·FLW), M4(자동화 완성: RUL·ACT·FLW·OPS·DEV), M5(데이터 관리: DSC·TSD·DEV·ING),
 * M6(분석 ANA — 생산자는 Python data2flow-analytics, AI AIA)에서
 * 쓰는 종류가 있다. 다음 마일스톤이 종류를 더할 때는 여기에 상수를 추가하고
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
    /** 실행 초기화(API-SIM-34 reset → CREATED). 시뮬레이터가 M3부터 내던 키 */
    SIM_RUN_RESET("sim.run.reset", "EVT-SIM-01", SimRunChanged.class),
    SIM_FAULT_STARTED("sim.fault.started", "EVT-SIM-02", SimFaultLabel.class),
    SIM_FAULT_ENDED("sim.fault.ended", "EVT-SIM-02", SimFaultLabel.class),
    SIM_DATA_PURGED("sim.data.purged", "EVT-SIM-04", SimDataPurged.class),

    FLOW_APPLY_REPORTED("flow.apply.reported", "EVT-FLW-02", FlowApplyReported.class),
    FLOW_STATE_CHANGED("flow.state.changed", "EVT-FLW-03", FlowStateChanged.class),

    // M4 자동화 완성: 규칙·알람·알림(RUL), 비상 정지·효과·드라이버·진동(ACT), 승격(FLW), 운영(OPS), 게이트웨이(DEV)
    ALARM_SIGNAL("alarm.signal", "EVT-RUL-01", AlarmSignal.class),
    ALARM_RAISED("alarm.raised", "EVT-RUL-02", AlarmStateChanged.class),
    ALARM_RERAISED("alarm.reraised", "EVT-RUL-02", AlarmStateChanged.class),
    ALARM_ACKED("alarm.acked", "EVT-RUL-02", AlarmStateChanged.class),
    ALARM_CLEARED("alarm.cleared", "EVT-RUL-02", AlarmStateChanged.class),
    ALARM_FLAPPING("alarm.flapping", "EVT-RUL-02", AlarmStateChanged.class),
    ALARM_SUPPRESSED("alarm.suppressed", "EVT-RUL-02", AlarmStateChanged.class),
    NOTIFICATION_DELIVERED("notification.delivered", "EVT-RUL-04", NotificationDeliveryResult.class),
    NOTIFICATION_FAILED("notification.failed", "EVT-RUL-04", NotificationDeliveryResult.class),
    CONTROL_EMERGENCY_STARTED("control.emergency.started", "EVT-ACT-03", EmergencyStopChanged.class),
    CONTROL_EMERGENCY_RELEASED("control.emergency.released", "EVT-ACT-03", EmergencyStopChanged.class),
    COMMAND_NO_EFFECT("command.no-effect", "EVT-ACT-04", CommandNoEffect.class),
    DRIVER_CIRCUIT_OPENED("driver.circuit.opened", "EVT-ACT-05", DriverCircuitChanged.class),
    DRIVER_CIRCUIT_CLOSED("driver.circuit.closed", "EVT-ACT-05", DriverCircuitChanged.class),
    CONTROL_OSCILLATION_BLOCKED("control.oscillation.blocked", "EVT-ACT-08", OscillationBlocked.class),
    LORAWAN_DOWNLINK_ACK("lorawan.downlink.ack", "EVT-ACT-09", LoRaWanDownlinkAck.class),
    FLOW_PROMOTED("flow.promoted", "EVT-FLW-06", FlowPromoted.class),
    OPS_ALARM_RAISED("ops.alarm.raised", "EVT-OPS-01", OpsAlarmChanged.class),
    OPS_ALARM_CLEARED("ops.alarm.cleared", "EVT-OPS-01", OpsAlarmChanged.class),
    OPS_MAINTENANCE_STARTED("ops.maintenance.started", "EVT-OPS-02", MaintenanceChanged.class),
    OPS_MAINTENANCE_ENDED("ops.maintenance.ended", "EVT-OPS-02", MaintenanceChanged.class),
    GATEWAY_CONNECTIVITY_CHANGED("gateway.connectivity.changed", "EVT-DEV-08", GatewayConnectivityChanged.class),

    // M5 데이터 관리: 외부 맥락·자격증명·엣지(DSC), 내보내기·가져오기·보관(TSD), 작업 지시·운영 모드·설치(DEV), 재처리(ING)
    CREDENTIAL_REVOKED("credential.revoked", "EVT-DSC-06", CredentialRevoked.class),
    CALENDAR_SYNCED("calendar.synced", "EVT-DSC-07", CalendarSynced.class),
    SOURCE_ROTATION_PROGRESS("source.rotation.progress", "EVT-DSC-08", SourceRotationProgress.class),
    EDGE_STATUS_CHANGED("edge.status.changed", "EVT-DSC-10", EdgeEvent.class),
    EDGE_CONFIG_APPLIED("edge.config.applied", "EVT-DSC-10", EdgeEvent.class),
    EDGE_BUFFER_DROPPED("edge.buffer.dropped", "EVT-DSC-10", EdgeEvent.class),
    EXPORT_COMPLETED("export.completed", "EVT-TSD-01", ExportJobFinished.class),
    EXPORT_FAILED("export.failed", "EVT-TSD-01", ExportJobFinished.class),
    IMPORT_COMPLETED("import.completed", "EVT-TSD-02", ImportCompleted.class),
    RETENTION_PURGED("retention.purged", "EVT-TSD-04", RetentionPurged.class),
    BI_EXPORT_COMPLETED("bi.export.completed", "EVT-TSD-07", BiExportFinished.class),
    BI_EXPORT_FAILED("bi.export.failed", "EVT-TSD-07", BiExportFinished.class),
    SPACE_MODE_CHANGED("space.mode.changed", "EVT-DEV-06", SpaceModeChanged.class),
    WORKORDER_CHANGED("workorder.changed", "EVT-DEV-09", WorkOrderChanged.class),
    DEVICE_EXPORT_COMPLETED("device.export.completed", "EVT-DEV-13", DeviceExportCompleted.class),
    DEVICE_COMMISSIONING_CHANGED("device.commissioning.changed", "EVT-DEV-14", DeviceCommissioningChanged.class),
    INGEST_REPROCESS_FINISHED("ingest.reprocess.finished", "EVT-ING-09", ReprocessJobFinished.class),

    // M6 분석(ANA, 생산 data2flow-analytics Python)·AI(AIA)
    ANALYTICS_RUN_QUEUED("analytics.run.queued", "EVT-ANA-01", AnalyticsRunStatusChanged.class),
    ANALYTICS_RUN_PENDING("analytics.run.pending", "EVT-ANA-01", AnalyticsRunStatusChanged.class),
    ANALYTICS_RUN_RUNNING("analytics.run.running", "EVT-ANA-01", AnalyticsRunStatusChanged.class),
    ANALYTICS_RUN_SUCCEEDED("analytics.run.succeeded", "EVT-ANA-01", AnalyticsRunStatusChanged.class),
    ANALYTICS_RUN_FAILED("analytics.run.failed", "EVT-ANA-01", AnalyticsRunStatusChanged.class),
    ANALYTICS_RUN_TIMEOUT("analytics.run.timeout", "EVT-ANA-01", AnalyticsRunStatusChanged.class),
    ANALYTICS_RUN_CANCELLED("analytics.run.cancelled", "EVT-ANA-01", AnalyticsRunStatusChanged.class),
    ANALYTICS_ANOMALY_DETECTED("analytics.anomaly.detected", "EVT-ANA-02", AnalyticsAnomalyDetected.class),
    ANALYTICS_ETA_UPDATED("analytics.eta.updated", "EVT-ANA-03", AnalyticsEtaUpdated.class),
    ANALYTICS_MODEL_DRIFT("analytics.model.drift", "EVT-ANA-04", AnalyticsModelDrift.class),
    ANALYTICS_SCHEDULE_STOPPED("analytics.schedule.stopped", "EVT-ANA-05", AnalyticsScheduleStopped.class),
    ANALYTICS_EXPORT_COMPLETED("analytics.export.completed", "EVT-ANA-06", AnalyticsExportCompleted.class),
    AI_QUOTA_EXCEEDED("ai.quota.exceeded", "EVT-AIA-03", AiQuotaExceeded.class);

    /** EVT-RUL-02 라우팅 키 접두사 */
    public static final String ALARM_PREFIX = "alarm.";

    /** EVT-SIM-01 라우팅 키 접두사 */
    public static final String SIM_RUN_PREFIX = "sim.run.";

    /** EVT-ANA-01 라우팅 키 접두사({@code analytics.run.*}로 바인딩한다) */
    public static final String ANALYTICS_RUN_PREFIX = "analytics.run.";

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

    /** 이 종류의 현재 스키마 버전. 모든 이벤트는 1 */
    public int version() {
        return DomainEvent.VERSION;
    }

    /** EVT-ACT-01 상태별 종류. UNKNOWN이면 {@link IllegalArgumentException} */
    public static EventType commandStatus(CommandStatus status) {
        return fromRoutingKey(status == CommandStatus.UNKNOWN ? "" : status.routingKey())
                .orElseThrow(() -> new IllegalArgumentException("발행할 수 없는 명령 상태입니다: " + status));
    }

    /**
     * EVT-SIM-01 실행 상태 이벤트 종류. {@code event}는 라우팅 키 끝(started, paused, resumed, stopped, completed, failed, throttled, reset)
     */
    public static EventType simRun(String event) {
        return fromRoutingKey(SIM_RUN_PREFIX + event)
                .orElseThrow(() -> new IllegalArgumentException("모르는 실행 상태 이벤트입니다: " + event));
    }

    /**
     * EVT-RUL-02 알람 상태 이벤트 종류. {@code event}는 라우팅 키 끝(raised, reraised, acked, cleared, flapping, suppressed).
     * {@code signal}(EVT-RUL-01)은 상태 이벤트가 아니므로 거부한다
     */
    public static EventType alarmState(String event) {
        EventType type = fromRoutingKey(ALARM_PREFIX + event)
                .orElseThrow(() -> new IllegalArgumentException("모르는 알람 상태 이벤트입니다: " + event));
        if (type == ALARM_SIGNAL) {
            throw new IllegalArgumentException("alarm.signal은 상태 이벤트가 아닙니다");
        }
        return type;
    }

    /** EVT-ANA-01 실행 상태별 종류. UNKNOWN이면 {@link IllegalArgumentException} */
    public static EventType analyticsRun(AnalyticsRunStatusChanged.Status status) {
        return fromRoutingKey(status == AnalyticsRunStatusChanged.Status.UNKNOWN ? "" : ANALYTICS_RUN_PREFIX + status.routingSuffix())
                .orElseThrow(() -> new IllegalArgumentException("발행할 수 없는 분석 실행 상태입니다: " + status));
    }

    /** 라우팅 키로 찾는다. 이 코드가 모르는 종류면 빈 값(소비자는 무시한다) */
    public static Optional<EventType> fromRoutingKey(String routingKey) {
        return Optional.ofNullable(BY_ROUTING_KEY.get(routingKey));
    }
}
