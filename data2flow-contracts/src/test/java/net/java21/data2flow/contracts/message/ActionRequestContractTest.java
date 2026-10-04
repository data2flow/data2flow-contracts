package net.java21.data2flow.contracts.message;

import net.java21.data2flow.contracts.command.ActionIdempotencyKeys;
import net.java21.data2flow.contracts.command.ActionKind;
import net.java21.data2flow.contracts.command.CommandPayload;
import net.java21.data2flow.contracts.command.CommandPriority;
import net.java21.data2flow.contracts.command.CommandSource;
import net.java21.data2flow.contracts.command.CommandTarget;
import net.java21.data2flow.contracts.messaging.MessageHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ACT-02.01·FLW-05.01: 행동 요청 ActionRequest v1(ACT-api §5.1, EVT-FLW-05) */
class ActionRequestContractTest {

    private static final Instant T = Instant.parse("2026-10-03T01:12:03.450Z");
    private static final Clock CLOCK = Clock.fixed(T, ZoneOffset.UTC);
    private final MessageCodec codec = MessageCodec.create();

    static ActionRequest flowCommand() {
        CommandSource source = CommandSource.flow("f-7f3a", 13, "n-act-1", "7f3a9c1e-2b4d-4f6a-8c0e-1a2b3c4d5e6f");
        CommandPayload payload = new CommandPayload(CommandTarget.space(31, "controls", "Thermostat", false), "Thermostat", "set",
                Map.of("mode", "cool", "targetTemperature", 24), true);
        return ActionRequest.command(1, ActionIdempotencyKeys.flow("f-7f3a", "n-act-1", source.triggerMessageId()), source,
                T.plusSeconds(600), payload, CLOCK);
    }

    @Test
    @DisplayName("ACT-02.01 TC-ACT-027 flow-engine이 만든 요청이 action-request.v1.json을 통과하고 action이 손실 없이 읽는다")
    void roundTrip() {
        ActionRequest request = flowCommand();
        MessageSchemas.assertValid(request);
        ActionRequest read = codec.read(codec.write(request), ActionRequest.class);
        assertThat(read).isEqualTo(request);
        assertThat(read.commandPayload()).isEqualTo(request.commandPayload());
        assertThat(read.priority()).isEqualTo(CommandPriority.AUTO);
        assertThat(read.routingKey()).isEqualTo("command");
        assertThat(read.kind()).isEqualTo(ActionKind.COMMAND);
        assertThat(read.expiredAt(T.plusSeconds(600))).isFalse();
        assertThat(read.expiredAt(T.plusSeconds(601))).isTrue();
        JsonNode tree = codec.toTree(request);
        assertThat(tree.propertyNames()).containsExactly("v", "messageId", "idempotencyKey", "kind", "organizationId", "createdAt",
                "source", "priority", "validUntil", "payload");
        assertThat(tree.at("/payload/target/relation").asString()).isEqualTo("controls");
        assertThat(tree.get("createdAt").asString()).isEqualTo("2026-10-03T01:12:03.450Z");
    }

    @Test
    @DisplayName("ACT-02.01 TC-ACT-027 모르는 필드는 무시하고 v2·형식 오류는 DLQ 대상 예외")
    void compatibility() {
        ObjectNode tree = (ObjectNode) codec.toTree(flowCommand());
        tree.put("futureField", 1);
        ((ObjectNode) tree.get("payload")).put("futurePayloadField", true);
        ActionRequest read = codec.read(codec.mapper().writeValueAsBytes(tree), ActionRequest.class);
        assertThat(read.commandPayload()).isEqualTo(flowCommand().commandPayload());

        ObjectNode v2 = tree.deepCopy();
        v2.put("v", 2);
        assertThatThrownBy(() -> codec.read(codec.mapper().writeValueAsBytes(v2), ActionRequest.class))
                .isInstanceOf(UnsupportedSchemaVersionException.class);
        ObjectNode badKey = tree.deepCopy();
        badKey.put("idempotencyKey", "has space");
        assertThatThrownBy(() -> codec.read(codec.mapper().writeValueAsBytes(badKey), ActionRequest.class))
                .isInstanceOf(MessageFormatException.class).hasMessageContaining("idempotencyKey");
        ObjectNode noSource = tree.deepCopy();
        noSource.remove("source");
        assertThatThrownBy(() -> codec.read(codec.mapper().writeValueAsBytes(noSource), ActionRequest.class))
                .isInstanceOf(MessageFormatException.class);
        ObjectNode arrayPayload = tree.deepCopy();
        arrayPayload.putArray("payload");
        assertThatThrownBy(() -> codec.read(codec.mapper().writeValueAsBytes(arrayPayload), ActionRequest.class))
                .isInstanceOf(MessageFormatException.class);
        ObjectNode badPayload = tree.deepCopy();
        ((ObjectNode) badPayload.get("payload")).remove("target");
        ActionRequest broken = codec.read(codec.mapper().writeValueAsBytes(badPayload), ActionRequest.class);
        assertThatThrownBy(broken::commandPayload).isInstanceOf(MessageFormatException.class);
        assertThat(MessageSchemas.validate(MessageSchemas.ACTION_REQUEST, badPayload)).isNotEmpty();
    }

    @Test
    @DisplayName("ACT-02.03 BR-ACT-24 command()는 출처로 우선순위를 정하고, 다른 종류는 commandPayload를 주지 않는다")
    void factories() {
        ActionRequest user = ActionRequest.command(1, "ui-1", CommandSource.user(7), null,
                new CommandPayload(CommandTarget.device(15), "Switch", "set", Map.of("on", false), false), CLOCK);
        assertThat(user.priority()).isEqualTo(CommandPriority.MANUAL);
        assertThat(user.validUntil()).isNull();
        assertThat(user.expiredAt(T.plusSeconds(99999))).isFalse();
        MessageSchemas.assertValid(user);
        ActionRequest notify = ActionRequest.of(ActionKind.NOTIFY, 1, "n-1", CommandSource.system(), CommandPriority.SAFETY, null,
                codec.mapper().createObjectNode().put("alarmId", 5).put("event", "alarm.raised").put("policyId", 3), CLOCK);
        assertThat(notify.routingKey()).isEqualTo("notify");
        assertThatThrownBy(notify::commandPayload).isInstanceOf(MessageFormatException.class).hasMessageContaining("NOTIFY");
        MessageSchemas.assertValid(notify);
    }

    @Test
    @DisplayName("OPS-02.03 공통 헤더: messageId·v·schema=action-request·organizationId")
    void headers() {
        ActionRequest request = flowCommand();
        Map<String, Object> headers = MessageHeaders.of(request);
        assertThat(headers).containsEntry(MessageHeaders.MESSAGE_ID, request.messageId().toString())
                .containsEntry(MessageHeaders.SCHEMA_VERSION, "1")
                .containsEntry(MessageHeaders.SCHEMA, "action-request")
                .containsEntry(MessageHeaders.ORGANIZATION_ID, "1");
        assertThat(MessageSchemas.fileOf(ActionRequest.class)).isEqualTo(MessageSchemas.ACTION_REQUEST);
    }
}
