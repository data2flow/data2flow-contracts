package net.java21.data2flow.contracts.message;

import net.java21.data2flow.contracts.capability.StateChange;
import net.java21.data2flow.contracts.command.CommandPriority;
import net.java21.data2flow.contracts.command.CommandSource;
import net.java21.data2flow.contracts.command.CommandStatus;
import net.java21.data2flow.contracts.connector.AckMode;
import net.java21.data2flow.contracts.connector.AuthMethod;
import net.java21.data2flow.contracts.connector.ConnectionErrorKind;
import net.java21.data2flow.contracts.connector.ConnectorCatalogEntry;
import net.java21.data2flow.contracts.connector.ConnectorCategory;
import net.java21.data2flow.contracts.connector.ConnectorState;
import net.java21.data2flow.contracts.connector.PayloadFormat;
import net.java21.data2flow.contracts.connector.ScalingMode;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ING-05.01·DSC-09.02·DEV-02.01: 도메인 이벤트 봉투 DomainEvent v1과 M2 이벤트 페이로드(EVT-DEV·DSC·ING·TSD) */
class DomainEventContractTest {

    private static final Instant T = Instant.parse("2026-10-03T02:40:09Z");
    private static final Clock CLOCK = Clock.fixed(T, ZoneOffset.UTC);
    private final MessageCodec codec = MessageCodec.create();

    static final Map<EventType, EventPayload> SAMPLES = new EnumMap<>(EventType.class);

    static {
        SAMPLES.put(EventType.DEVICE_CHANGED, new DeviceChanged(17, DeviceChanged.Change.APPROVED, List.of("status", "spaceId"),
                "ACTIVE", "6", 31L, 4));
        SAMPLES.put(EventType.DEVICE_CONNECTIVITY_CHANGED, new DeviceConnectivityChanged(17,
                DeviceConnectivityChanged.Connectivity.ONLINE, DeviceConnectivityChanged.Connectivity.OFFLINE, T, 60, 3));
        SAMPLES.put(EventType.DEVICE_PENDING_CREATED, new DevicePendingCreated(17, 3, "24e124743d012436", "WS302-012436", T));
        SAMPLES.put(EventType.SPACE_CHANGED, new SpaceChanged(31, "MOVED", "/1/7/31"));
        SAMPLES.put(EventType.GROUP_MEMBERSHIP_CHANGED, new GroupMembershipChanged(5, List.of(17L, 18L), List.of()));
        SAMPLES.put(EventType.SOURCE_RUNTIME_REPORTED, new SourceRuntimeReported(3, "data2flow-ingress-0",
                ConnectorState.ERROR, ConnectionErrorKind.AUTH, "401 Unauthorized", "data2flow-ingress-prod-0", null, 2));
        SAMPLES.put(EventType.SOURCE_STATS_1M, new SourceStatsReported(3, T, SourceStatsReported.Producer.INGRESS,
                Map.of("received", 120L, "rejected", 0L)));
        SAMPLES.put(EventType.SOURCE_CONNECTION_CHANGED, new SourceConnectionChanged(3, ConnectorState.CONNECTED,
                ConnectorState.DISCONNECTED, null, T));
        SAMPLES.put(EventType.SOURCE_NO_DATA, new SourceDataActivity(3, T, 600));
        SAMPLES.put(EventType.SOURCE_DATA_RESUMED, new SourceDataActivity(3, T, 600));
        SAMPLES.put(EventType.CONNECTOR_CATALOG_REPORTED, new ConnectorCatalogReported("data2flow-ingress-0", List.of(
                new ConnectorCatalogEntry("mqtt", "MQTT", "1.0.0", ConnectorCategory.MQTT,
                        MessageCodec.create().mapper().readTree("{\"type\":\"object\"}"),
                        Set.of(AuthMethod.USER_PASSWORD, AuthMethod.WS_HEADER), Set.of(PayloadFormat.JSON),
                        AckMode.AFTER_WRITE, ScalingMode.DUAL_ACTIVE, true))));
        SAMPLES.put(EventType.METRIC_UNVERIFIED_REGISTERED, new MetricUnverifiedRegistered("tvoc", 17, T));
        SAMPLES.put(EventType.INGEST_ALERT_RAISED, IngestAlert.of(IngestAlert.INGEST_LAG, IngestAlert.Level.WARNING, null,
                75, 60, List.of("pipeline 인스턴스 1대"), T));
        SAMPLES.put(EventType.INGEST_ALERT_CLEARED, new IngestAlert(IngestAlert.SCRIPT_ERROR_RATE,
                IngestAlert.Level.CRITICAL, 3L, 0.4, 0.2, null, T, 42L, 3, 0.4, "5m", true));
        SAMPLES.put(EventType.INGEST_GAP_DETECTED, new IngestGapDetected(17, T.minusSeconds(600), T, 9));
        SAMPLES.put(EventType.INGEST_CLOCK_SKEW_SUSPECTED, new ClockSkewSuspected(17, -420.5, T));
        SAMPLES.put(EventType.AGGREGATES_RECOMPUTED, new AggregatesRecomputed("1h",
                List.of(new AggregatesRecomputed.Item(17, "co2", T.minusSeconds(3600), T))));
        SAMPLES.put(EventType.PARTITION_WARNING, new PartitionWarning("data2flow_pipeline.telemetry",
                PartitionWarning.DEFAULT_PARTITION_ROWS, "12 rows"));

        // M3 가상 폐루프
        for (CommandStatus status : CommandStatus.values()) {
            if (status != CommandStatus.UNKNOWN) {
                SAMPLES.put(EventType.commandStatus(status), new CommandStatusChanged(UUID.fromString(
                        "8f1c2d3e-0000-4000-8000-000000000001"), "6a0f1f4b", 15, 31L, "Thermostat", "set",
                        Map.of("mode", "cool", "targetTemperature", 24), status, status.terminal() ? "NO_CHANGE" : null, null,
                        CommandSource.flow("f-7f3a", 13, "n-act-1", "m-1"), CommandPriority.AUTO, T));
            }
        }
        SAMPLES.put(EventType.DEVICE_STATE_CHANGED, new DeviceStateChanged(15, 31L, DeviceStateChanged.Connectivity.ONLINE,
                Map.of("Thermostat", Map.of("mode", "cool", "targetTemperature", 24)),
                List.of(new StateChange("Thermostat", "mode", "off", "cool")), 8, Map.of(), T, DeviceStateChanged.Origin.COMMAND));
        SAMPLES.put(EventType.DEVICE_COMMAND_ACK, DeviceCommandAck.acked("8f1c2d3e-0000-4000-8000-000000000001", 15, T, true));
        SAMPLES.put(EventType.DEVICE_STATE_REPORTED, new DeviceStateReported(15, 8,
                Map.of("Switch", Map.of("on", true), "Thermostat", Map.of("mode", "cool")), T, true));
        for (String run : List.of("started", "paused", "resumed", "stopped", "completed", "failed", "throttled")) {
            SAMPLES.put(EventType.simRun(run), new SimRunChanged(1, 42, run.equals("failed") ? null : 3L, "RUNNING", T, 60,
                    run.equals("stopped") ? Boolean.TRUE : null, run.equals("failed") ? "tick error" : null, T));
        }
        SAMPLES.put(EventType.SIM_FAULT_STARTED, new SimFaultLabel(1, 42L, 9, "STUCK", "SENSOR", "21", T, null,
                Map.of("value", 27.5)));
        SAMPLES.put(EventType.SIM_FAULT_ENDED, new SimFaultLabel(1, null, 9, "STUCK", "SENSOR", "21", T, T.plusSeconds(1800),
                Map.of()));
        SAMPLES.put(EventType.SIM_DATA_PURGED, new SimDataPurged(1, "job-1", List.of(42L), null, null,
                new SimDataPurged.DeletedRows(1200, 40, 2, 5)));
        SAMPLES.put(EventType.FLOW_APPLY_REPORTED, new FlowApplyReported("f-7f3a", "data2flow-flow-engine-0", 13, 2L, 41, null));
        SAMPLES.put(EventType.FLOW_STATE_CHANGED, new FlowStateChanged("f-7f3a", "ACTIVE", "DEGRADED",
                FlowStateChanged.Reason.DEGRADED, new FlowStateChanged.Metrics(0.3, 12.5), T));
    }

    @ParameterizedTest
    @EnumSource(EventType.class)
    @DisplayName("ING-05.01 TC-ING-065 모든 M2·M3 이벤트가 봉투 스키마(domain-event.v1.json)를 통과하고 같은 값으로 읽힌다")
    void everyEventRoundTripsAndMatchesSchema(EventType type) {
        EventPayload payload = SAMPLES.get(type);
        assertThat(payload).as("샘플 누락: " + type).isNotNull();
        DomainEvent<EventPayload> event = DomainEvent.of(type, 1, payload, "req-1", CLOCK);
        MessageSchemas.assertValid(event);
        DomainEvent<? extends EventPayload> read = codec.readEvent(codec.write(event));
        assertThat(read).isEqualTo(event);
        assertThat(read.eventType()).isEqualTo(type);
        assertThat(read.payload()).isInstanceOf(type.payloadType());
    }

    @Test
    @DisplayName("ING-05.01 봉투는 v·messageId·type·organizationId·occurredAt·requestId·payload")
    void envelopeShape() {
        DomainEvent<DevicePendingCreated> event = DomainEvent.of(EventType.DEVICE_PENDING_CREATED, 1,
                (DevicePendingCreated) SAMPLES.get(EventType.DEVICE_PENDING_CREATED), null, CLOCK);
        JsonNode tree = codec.toTree(event);
        assertThat(tree.propertyNames()).containsExactly("v", "messageId", "type", "organizationId", "occurredAt", "payload");
        assertThat(tree.get("type").asString()).isEqualTo("device.pending.created");
        assertThat(tree.get("occurredAt").asString()).isEqualTo("2026-10-03T02:40:09Z");
        assertThat(tree.at("/payload/externalId").asString()).isEqualTo("24e124743d012436");
        assertThat(EventType.DEVICE_PENDING_CREATED.eventId()).isEqualTo("EVT-DEV-03");
    }

    @Test
    @DisplayName("ING-05.01 스키마의 x-payloadTypes와 EventType 목록이 같다(계약과 코드 동기화)")
    void schemaAndEnumAreInSync() {
        JsonNode schema = MessageSchemas.raw(MessageSchemas.DOMAIN_EVENT);
        Set<String> inSchema = schema.get("x-payloadTypes").propertyNames().stream().collect(Collectors.toSet());
        Set<String> inCode = java.util.Arrays.stream(EventType.values()).map(EventType::routingKey).collect(Collectors.toSet());
        assertThat(inSchema).isEqualTo(inCode);
        assertThat(schema.get("allOf").size()).isEqualTo(EventType.values().length);
    }

    @Test
    @DisplayName("ING-05.01 TC-ING-065 페이로드의 모르는 필드는 무시하고, 페이로드 스키마 위반은 스키마 검사에서 걸린다")
    void unknownPayloadFieldsIgnoredAndSchemaCatchesViolations() {
        DomainEvent<EventPayload> event = DomainEvent.of(EventType.DEVICE_CHANGED, 1, SAMPLES.get(EventType.DEVICE_CHANGED),
                null, CLOCK);
        ObjectNode tree = (ObjectNode) codec.toTree(event);
        ((ObjectNode) tree.get("payload")).put("futureField", true);
        assertThat(codec.readEvent(codec.mapper().writeValueAsBytes(tree), DeviceChanged.class).payload())
                .isEqualTo(SAMPLES.get(EventType.DEVICE_CHANGED));
        ((ObjectNode) tree.get("payload")).put("change", "EXPLODED");
        assertThat(MessageSchemas.validate(MessageSchemas.DOMAIN_EVENT, tree)).isNotEmpty();
    }

    @Test
    @DisplayName("모르는 종류·잘못된 페이로드 타입·모르는 버전은 거부한다")
    void rejections() {
        DomainEvent<EventPayload> event = DomainEvent.of(EventType.SPACE_CHANGED, 1, SAMPLES.get(EventType.SPACE_CHANGED),
                null, CLOCK);
        String json = codec.writeAsString(event);
        assertThatThrownBy(() -> codec.readEvent(json.replace("space.changed", "space.exploded").getBytes()))
                .isInstanceOf(MessageFormatException.class).hasMessageContaining("space.exploded");
        assertThatThrownBy(() -> codec.readEvent(json.replace("\"type\":\"space.changed\",", "").getBytes()))
                .isInstanceOf(MessageFormatException.class).hasMessageContaining("type");
        assertThatThrownBy(() -> codec.readEvent(json.replace("\"v\":1", "\"v\":2").getBytes()))
                .isInstanceOf(UnsupportedSchemaVersionException.class).hasMessageContaining("space.changed");
        assertThatThrownBy(() -> codec.readEvent(json.getBytes(), DeviceChanged.class))
                .isInstanceOf(MessageFormatException.class).hasMessageContaining("DeviceChanged");
        assertThatThrownBy(() -> codec.read(json, DomainEvent.class)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DomainEvent.of(EventType.DEVICE_CHANGED, 1, SAMPLES.get(EventType.SPACE_CHANGED), null, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DomainEvent<>(1, event.messageId(), "x.y", 1, T, null, null))
                .isInstanceOf(MessageFormatException.class);
        assertThatThrownBy(() -> new DomainEvent<>(1, event.messageId(), "x.y", 1, T, null,
                SAMPLES.get(EventType.SPACE_CHANGED)).eventType()).isInstanceOf(MessageFormatException.class);
        assertThat(EventType.fromRoutingKey("nope")).isEmpty();
    }

    @Test
    @DisplayName("SIM-03.04 TC-ACT-038 BR-ACT-25 시뮬레이터가 지금 보내는 ack·reported·실행·장애 JSON과 바이트 모양이 같다(전선 변경 없음)")
    void simulatorWireShapeUnchanged() {
        // data2flow-simulator sim/contracts 로컬 record가 만들던 JSON(필드 순서 포함)
        String ack = "{\"v\":1,\"messageId\":\"5d0c7c1a-9a51-4c43-bf0e-0d6f2d6c4a11\",\"type\":\"device.command.ack\","
                + "\"organizationId\":1,\"occurredAt\":\"2026-10-03T02:40:09Z\",\"payload\":{\"commandId\":\"c-1\","
                + "\"deviceId\":15,\"result\":\"FAILED\",\"reason\":\"INVALID_COMMAND\",\"at\":\"2026-10-03T02:40:09Z\","
                + "\"virtual\":true}}";
        String run = "{\"v\":1,\"messageId\":\"5d0c7c1a-9a51-4c43-bf0e-0d6f2d6c4a12\",\"type\":\"sim.run.stopped\","
                + "\"organizationId\":1,\"occurredAt\":\"2026-10-03T02:40:09Z\",\"payload\":{\"organizationId\":1,"
                + "\"runId\":42,\"scenarioId\":3,\"status\":\"STOPPED\",\"simClock\":\"2026-07-15T05:00:00Z\","
                + "\"accelerationEffective\":60,\"partial\":true,\"at\":\"2026-10-03T02:40:09Z\"}}";
        String fault = "{\"v\":1,\"messageId\":\"5d0c7c1a-9a51-4c43-bf0e-0d6f2d6c4a13\",\"type\":\"sim.fault.ended\","
                + "\"organizationId\":1,\"occurredAt\":\"2026-10-03T02:40:09Z\",\"payload\":{\"organizationId\":1,"
                + "\"runId\":42,\"faultId\":9,\"kind\":\"STUCK\",\"targetType\":\"SENSOR\",\"targetId\":\"21\","
                + "\"simFrom\":\"2026-07-15T05:00:00Z\",\"simTo\":\"2026-07-15T05:30:00Z\",\"params\":{\"value\":27.5}}}";
        String reported = "{\"v\":1,\"messageId\":\"5d0c7c1a-9a51-4c43-bf0e-0d6f2d6c4a14\",\"type\":\"device.state.reported\","
                + "\"organizationId\":1,\"occurredAt\":\"2026-10-03T02:40:09Z\",\"payload\":{\"deviceId\":15,\"version\":8,"
                + "\"capabilities\":{\"Switch\":{\"on\":true}},\"reportedAt\":\"2026-10-03T02:40:09Z\",\"virtual\":true}}";
        for (String json : List.of(ack, run, fault, reported)) {
            DomainEvent<? extends EventPayload> event = codec.readEvent(json.getBytes());
            MessageSchemas.assertValid(event);
            assertThat(codec.writeAsString(event)).isEqualTo(json);
        }
        assertThat(codec.readEvent(ack.getBytes(), DeviceCommandAck.class).payload().result())
                .isEqualTo(DeviceCommandAck.Result.FAILED);
        assertThat(DeviceCommandAck.failed("c-1", 15, "INVALID_COMMAND", T, true).reason()).isEqualTo("INVALID_COMMAND");
    }

    @Test
    @DisplayName("ACT-02.02 EVT-ACT-01 상태별 이벤트 종류와 EVT-SIM-01 실행 이벤트 종류를 찾는다")
    void m3TypeLookups() {
        assertThat(EventType.commandStatus(CommandStatus.APPLIED).routingKey()).isEqualTo("command.status.applied");
        assertThat(EventType.commandStatus(CommandStatus.QUEUED_FOR_DOWNLINK).routingKey())
                .isEqualTo("command.status.queued_for_downlink");
        assertThat(EventType.commandStatus(CommandStatus.APPLIED).eventId()).isEqualTo("EVT-ACT-01");
        assertThatThrownBy(() -> EventType.commandStatus(CommandStatus.UNKNOWN)).isInstanceOf(IllegalArgumentException.class);
        assertThat(EventType.simRun("throttled")).isEqualTo(EventType.SIM_RUN_THROTTLED);
        assertThatThrownBy(() -> EventType.simRun("exploded")).isInstanceOf(IllegalArgumentException.class);
        assertThat(EventType.DEVICE_STATE_REPORTED.eventId()).isEqualTo("EVT-ACT-07");
        assertThat(codec.mapper().readValue("\"LOST\"", DeviceCommandAck.Result.class)).isEqualTo(DeviceCommandAck.Result.UNKNOWN);
    }
}
