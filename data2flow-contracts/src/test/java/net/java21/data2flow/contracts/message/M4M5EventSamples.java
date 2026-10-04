package net.java21.data2flow.contracts.message;

import net.java21.data2flow.contracts.alarm.AlarmClearReason;
import net.java21.data2flow.contracts.alarm.AlarmKeys;
import net.java21.data2flow.contracts.alarm.AlarmSeverity;
import net.java21.data2flow.contracts.alarm.AlarmSnapshot;
import net.java21.data2flow.contracts.alarm.AlarmSourceType;
import net.java21.data2flow.contracts.alarm.AlarmStatus;
import net.java21.data2flow.contracts.alarm.SuppressedReason;
import net.java21.data2flow.contracts.capability.ExpectedEffect;
import net.java21.data2flow.contracts.command.CommandSource;
import net.java21.data2flow.contracts.message.event.AlarmSignal;
import net.java21.data2flow.contracts.message.event.AlarmStateChanged;
import net.java21.data2flow.contracts.message.event.BiExportFinished;
import net.java21.data2flow.contracts.message.event.CalendarSynced;
import net.java21.data2flow.contracts.message.event.CommandNoEffect;
import net.java21.data2flow.contracts.message.event.CredentialRevoked;
import net.java21.data2flow.contracts.message.event.DeviceCommissioningChanged;
import net.java21.data2flow.contracts.message.event.DeviceConnectivityChanged;
import net.java21.data2flow.contracts.message.event.DeviceExportCompleted;
import net.java21.data2flow.contracts.message.event.DriverCircuitChanged;
import net.java21.data2flow.contracts.message.event.EdgeEvent;
import net.java21.data2flow.contracts.message.event.EmergencyStopChanged;
import net.java21.data2flow.contracts.message.event.EventPayload;
import net.java21.data2flow.contracts.message.event.ExportJobFinished;
import net.java21.data2flow.contracts.message.event.FlowPromoted;
import net.java21.data2flow.contracts.message.event.GatewayConnectivityChanged;
import net.java21.data2flow.contracts.message.event.ImportCompleted;
import net.java21.data2flow.contracts.message.event.MaintenanceChanged;
import net.java21.data2flow.contracts.message.event.NotificationDeliveryResult;
import net.java21.data2flow.contracts.message.event.OpsAlarmChanged;
import net.java21.data2flow.contracts.message.event.OscillationBlocked;
import net.java21.data2flow.contracts.message.event.ReprocessJobFinished;
import net.java21.data2flow.contracts.message.event.RetentionPurged;
import net.java21.data2flow.contracts.message.event.SourceRotationProgress;
import net.java21.data2flow.contracts.message.event.SpaceModeChanged;
import net.java21.data2flow.contracts.message.event.WorkOrderChanged;
import net.java21.data2flow.contracts.notification.DeliveryStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** M4(자동화 완성)·M5(데이터 관리) 도메인 이벤트 샘플. 모든 {@link EventType}이 샘플을 가져야 계약 테스트가 통과한다 */
final class M4M5EventSamples {

    static final Instant T = Instant.parse("2026-10-03T02:40:09Z");
    static final UUID COMMAND_ID = UUID.fromString("8f1c2d3e-0000-4000-8000-000000000001");

    private M4M5EventSamples() {
    }

    static AlarmSnapshot alarm(AlarmStatus status) {
        return new AlarmSnapshot(9001, AlarmKeys.flow("f-7f3a", "n-alarm-1", "15"), AlarmSeverity.MAJOR, status, false,
                "실습실 고온", new AlarmSnapshot.Source(AlarmSourceType.RULE, 12L, "f-7f3a", "n-alarm-1"),
                new AlarmSnapshot.Ref(15, "실습실 에어컨 센서"), new AlarmSnapshot.SpaceRef(31, "/1/7/31"), "temperature",
                27.4, 28.1, 27.9, 2, T.minusSeconds(600), T, status == AlarmStatus.ACKNOWLEDGED
                ? new AlarmSnapshot.UserRef(5, "김운영") : null, status == AlarmStatus.ACKNOWLEDGED ? T : null,
                status == AlarmStatus.CLEARED ? T : null, status == AlarmStatus.CLEARED ? AlarmClearReason.AUTO : null,
                null, null, null, null, status == AlarmStatus.SUPPRESSED ? SuppressedReason.MAINTENANCE : null);
    }

    static Map<EventType, EventPayload> all() {
        Map<EventType, EventPayload> m = new EnumMap<>(EventType.class);
        m.put(EventType.ALARM_SIGNAL, AlarmSignal.raise(AlarmKeys.flow("f-7f3a", "n-alarm-1", "15"), AlarmSourceType.RULE, 12L,
                "f-7f3a", 13, "n-alarm-1", AlarmSeverity.MAJOR, "실습실 고온", 15L, 31L, "temperature", 27.4,
                new AlarmSignal.Threshold(27.0, 26.0), T, "7f3a0000-0000-4000-8000-000000000001"));
        m.put(EventType.ALARM_RAISED, new AlarmStateChanged(alarm(AlarmStatus.ACTIVE), null, T));
        m.put(EventType.ALARM_RERAISED, new AlarmStateChanged(alarm(AlarmStatus.ACTIVE), null, T));
        m.put(EventType.ALARM_ACKED, new AlarmStateChanged(alarm(AlarmStatus.ACKNOWLEDGED), AlarmStateChanged.Actor.user(5), T));
        m.put(EventType.ALARM_CLEARED, new AlarmStateChanged(alarm(AlarmStatus.CLEARED),
                new AlarmStateChanged.Actor(AlarmStateChanged.Actor.Type.SYSTEM, null), T));
        m.put(EventType.ALARM_FLAPPING, new AlarmStateChanged(alarm(AlarmStatus.ACTIVE), null, T));
        m.put(EventType.ALARM_SUPPRESSED, new AlarmStateChanged(alarm(AlarmStatus.SUPPRESSED), null, T));
        m.put(EventType.NOTIFICATION_DELIVERED, new NotificationDeliveryResult(COMMAND_ID, 9001L, 2L, "TELEGRAM", "USER:5",
                DeliveryStatus.SENT, 1, null, T));
        m.put(EventType.NOTIFICATION_FAILED, new NotificationDeliveryResult(COMMAND_ID, 9001L, 2L, "TELEGRAM", "USER:5",
                DeliveryStatus.FAILED, 5, "403 Forbidden: bot was blocked by the user", T));
        m.put(EventType.CONTROL_EMERGENCY_STARTED, new EmergencyStopChanged(3, EmergencyStopChanged.Scope.space(31),
                "냉각수 누수", 1L, T));
        m.put(EventType.CONTROL_EMERGENCY_RELEASED, new EmergencyStopChanged(3, EmergencyStopChanged.Scope.organization(),
                null, 1L, T));
        m.put(EventType.COMMAND_NO_EFFECT, new CommandNoEffect(COMMAND_ID, 15, 31L, "Thermostat",
                new CommandNoEffect.Expected("temperature", ExpectedEffect.Direction.DOWN, 15),
                CommandNoEffect.Observed.between(27.0, 27.1), T));
        m.put(EventType.DRIVER_CIRCUIT_OPENED, new DriverCircuitChanged(7, "MQTT", 0.6, T));
        m.put(EventType.DRIVER_CIRCUIT_CLOSED, new DriverCircuitChanged(7, "MQTT", 0.0, T));
        m.put(EventType.CONTROL_OSCILLATION_BLOCKED, new OscillationBlocked(COMMAND_ID, 15, 31L, "Switch", "set", 3, 60,
                CommandSource.flow("f-7f3a", 13, "n-act-1", "m-9"), T));
        m.put(EventType.FLOW_PROMOTED, new FlowPromoted(4, 2, 11, "test", "prod", 1L, Map.of("f-7f3a", 14), T));
        m.put(EventType.OPS_ALARM_RAISED, new OpsAlarmChanged(77, "INGEST_ZERO", AlarmSeverity.CRITICAL, "pipeline", 0.0,
                1.0, T));
        m.put(EventType.OPS_ALARM_CLEARED, new OpsAlarmChanged(77, "INGEST_ZERO", AlarmSeverity.CRITICAL, "pipeline", 12.0,
                1.0, T));
        m.put(EventType.OPS_MAINTENANCE_STARTED, new MaintenanceChanged(8, MaintenanceChanged.TargetType.SPACE, 31,
                List.of(32L, 33L), true, true, T, T.plusSeconds(7200)));
        m.put(EventType.OPS_MAINTENANCE_ENDED, new MaintenanceChanged(8, MaintenanceChanged.TargetType.DEVICE, 15, List.of(),
                false, false, T, null));
        m.put(EventType.GATEWAY_CONNECTIVITY_CHANGED, new GatewayConnectivityChanged(2, "24e124fffef79304",
                DeviceConnectivityChanged.Connectivity.ONLINE, DeviceConnectivityChanged.Connectivity.OFFLINE, T));
        // M5
        m.put(EventType.CREDENTIAL_REVOKED, new CredentialRevoked(15, 4, "dev-15"));
        m.put(EventType.CALENDAR_SYNCED, new CalendarSynced(9, List.of(
                new CalendarSynced.Event("holiday:2026-10-09", "한글날", "HOLIDAY", LocalDate.parse("2026-10-09"),
                        LocalDate.parse("2026-10-09"), null, null),
                new CalendarSynced.Event("evt-1@school", "중간고사", "EXAM", LocalDate.parse("2026-10-20"),
                        LocalDate.parse("2026-10-24"), LocalTime.parse("09:00"), LocalTime.parse("18:00"))),
                List.of("evt-0@school")));
        m.put(EventType.SOURCE_ROTATION_PROGRESS, new SourceRotationProgress(3, "rot-1", "data2flow-ingress-0", true, null));
        m.put(EventType.EDGE_STATUS_CHANGED, EdgeEvent.statusChanged(5, "OFFLINE", T));
        m.put(EventType.EDGE_CONFIG_APPLIED, EdgeEvent.configApplied(5, 3, "APPLIED", T));
        m.put(EventType.EDGE_BUFFER_DROPPED, EdgeEvent.bufferDropped(5, 120, T));
        m.put(EventType.EXPORT_COMPLETED, new ExportJobFinished("exp-1", 5L, "COMPLETED", 525_600, "/files/exp-1.csv", null));
        m.put(EventType.EXPORT_FAILED, new ExportJobFinished("exp-2", null, "FAILED", 0, null, "storage unavailable"));
        m.put(EventType.IMPORT_COMPLETED, new ImportCompleted("imp-1", 1000, 3, 0, T.minusSeconds(86_400), T, List.of(15L)));
        m.put(EventType.RETENTION_PURGED, new RetentionPurged("raw", T.minusSeconds(86_400 * 40L), T.minusSeconds(86_400 * 30L),
                120_000, true));
        m.put(EventType.BI_EXPORT_COMPLETED, new BiExportFinished(6, 2, "s3://bi/2026-10-03.parquet", null, 1));
        m.put(EventType.BI_EXPORT_FAILED, new BiExportFinished(6, 2, null, "SFTP auth", 3));
        m.put(EventType.SPACE_MODE_CHANGED, new SpaceModeChanged(31, SpaceModeChanged.UNOCCUPIED, SpaceModeChanged.HOLIDAY,
                "CALENDAR", T));
        m.put(EventType.WORKORDER_CHANGED, new WorkOrderChanged(41, "BATTERY_REPLACE", null, "OPEN", List.of(15L), null, 5L));
        m.put(EventType.DEVICE_EXPORT_COMPLETED, new DeviceExportCompleted("dx-1", "DTDL", "COMPLETED", "files/dx-1.json"));
        m.put(EventType.DEVICE_COMMISSIONING_CHANGED, new DeviceCommissioningChanged(15, "FIRST_SEEN", T,
                Map.of("firstSeen", true, "space", true)));
        m.put(EventType.INGEST_REPROCESS_FINISHED, new ReprocessJobFinished("rp-1", ReprocessJobFinished.Status.COMPLETED, 3L,
                List.of(), T.minusSeconds(7 * 86_400L), T, 10_080, 10_075, 0, 5, 5L, null, T));
        return m;
    }
}
