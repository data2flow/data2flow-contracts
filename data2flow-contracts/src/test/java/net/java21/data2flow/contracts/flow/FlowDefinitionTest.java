package net.java21.data2flow.contracts.flow;

import net.java21.data2flow.contracts.message.MessageCodec;
import net.java21.data2flow.contracts.message.MessageSchemas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FLW-01.01·01.05·05.01: 플로우 정의 v1과 노드 카탈로그 항목(FLW-api §5, API-FLW-30) */
class FlowDefinitionTest {

    private static final JsonMapper MAPPER = MessageCodec.newMapper();

    /** M3 시연 템플릿 "고온이면 냉방": 온도 수신 → 27℃ 초과 5분 지속 → 에어컨 냉방 24℃ */
    static final String HOT_THEN_COOL = """
            {"schema":"data2flow.flow-definition/v1",
             "mode":{"concurrency":"queued","keyBy":"deviceId","max":10},
             "variables":[{"name":"lastAlert","type":"string","initial":""}],
             "nodes":[
               {"id":"n-trg-1","type":"trigger.telemetry","typeVersion":1,"name":"온도 수신",
                "config":{"target":{"spaceId":31,"relation":"measures","includeChildren":false},"metrics":["temperature"]},
                "position":{"x":100,"y":120}},
               {"id":"n-thr-1","type":"condition.threshold","typeVersion":1,"name":"온도>27",
                "config":{"metric":"temperature","op":">","value":27,"for":"PT5M","clear":26},
                "retry":{"maxAttempts":0},"position":{"x":420,"y":120}},
               {"id":"n-act-1","type":"action.control","typeVersion":1,"name":"냉방 24℃",
                "config":{"target":{"spaceId":31,"relation":"controls","capability":"Thermostat"},"command":"set",
                          "args":{"mode":"cool","targetTemperature":24},"awaitResult":true},
                "position":{"x":740,"y":120}}],
             "wires":[{"from":"n-trg-1","to":"n-thr-1"},{"from":"n-thr-1","port":"true","to":"n-act-1"}],
             "subflows":[]}
            """;

    @Test
    @DisplayName("FLW-01.05 TC-FLW-019 「고온이면 냉방」 템플릿이 flow-definition.v1.json을 통과하고 같은 값으로 다시 읽힌다")
    void template() {
        JsonNode json = MAPPER.readTree(HOT_THEN_COOL);
        MessageSchemas.assertValid(MessageSchemas.FLOW_DEFINITION, json);
        FlowDefinition flow = MAPPER.treeToValue(json, FlowDefinition.class);
        assertThat(flow.structuralErrors()).isEmpty();
        assertThat(flow.nodes()).extracting(FlowNode::id).containsExactly("n-trg-1", "n-thr-1", "n-act-1");
        assertThat(flow.wires().get(1).port()).isEqualTo("true");
        assertThat(flow.mode().keyBy()).isEqualTo("deviceId");
        assertThat(flow.nodes().get(2).config().at("/args/targetTemperature").asInt()).isEqualTo(24);
        JsonNode written = MAPPER.valueToTree(flow);
        MessageSchemas.assertValid(MessageSchemas.FLOW_DEFINITION, written);
        assertThat(MAPPER.treeToValue(written, FlowDefinition.class)).isEqualTo(flow);
        assertThat(written.has("subflows")).isFalse();
    }

    @Test
    @DisplayName("FLW-01.01 TC-FLW-001 구조 검사: 노드 ID 중복, 없는 노드 연결, 자기 연결, 변수 중복, 스키마 표시")
    void structuralErrors() {
        FlowNode a = new FlowNode("n-1", "trigger.manual", null, null, null, null, null, null, null);
        FlowNode dup = new FlowNode("n-1", "debug.log", null, null, null, null, null, null, null);
        FlowDefinition bad = new FlowDefinition("other/v9", null,
                List.of(new FlowDefinition.Variable("x", "number", null), new FlowDefinition.Variable("x", "number", null)),
                List.of(a, dup), List.of(new FlowDefinition.Wire("n-1", null, "n-9"), new FlowDefinition.Wire("n-8", null, "n-1"),
                new FlowDefinition.Wire("n-1", "out", "n-1")), null);
        assertThat(bad.structuralErrors()).hasSize(6).anyMatch(e -> e.contains("schema"))
                .anyMatch(e -> e.contains("노드 ID")).anyMatch(e -> e.contains("끝 노드")).anyMatch(e -> e.contains("시작 노드"))
                .anyMatch(e -> e.contains("자기 자신")).anyMatch(e -> e.contains("변수"));
        FlowDefinition empty = new FlowDefinition(FlowDefinition.SCHEMA, null, null, null, null, null);
        assertThat(empty.structuralErrors()).isEmpty();
        assertThatThrownBy(() -> new FlowDefinition.Wire(" ", null, "n")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FlowNode("n", " ", null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FlowNode(null, "a.b", null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        ObjectNode noSchema = (ObjectNode) MAPPER.readTree(HOT_THEN_COOL);
        noSchema.remove("schema");
        assertThat(MessageSchemas.validate(MessageSchemas.FLOW_DEFINITION, noSchema)).isNotEmpty();
        assertThat(FlowDefinition.MAX_BYTES).isEqualTo(2 * 1024 * 1024);
    }

    @Test
    @DisplayName("FLW-02 TC-FLW-031 노드 카탈로그 항목(condition.threshold)이 flow-node-type.v1.json을 통과하고 error 포트를 가진다")
    void nodeType() {
        FlowNodeType threshold = new FlowNodeType("condition.threshold", 1, "condition", "임계값", "값과 지속 시간 비교", "gauge",
                MAPPER.readTree("{\"type\":\"object\",\"required\":[\"metric\",\"op\"],\"properties\":{\"metric\":{\"type\":\"string\"}}}"),
                List.of(new FlowNodeType.Port("in", "telemetry", null)),
                List.of(new FlowNodeType.Port("true", "telemetry", null), new FlowNodeType.Port("false", "telemetry", null),
                        new FlowNodeType.Port(FlowNodeType.ERROR_PORT, "error", null)),
                MAPPER.readTree("{\"for\":\"RESET\"}"), List.of(), MAPPER.readTree("{\"timeoutMs\":1000}"));
        JsonNode json = MAPPER.valueToTree(threshold);
        MessageSchemas.assertValid(MessageSchemas.FLOW_NODE_TYPE, json);
        assertThat(MAPPER.treeToValue(json, FlowNodeType.class)).isEqualTo(threshold);
        FlowNodeType noError = new FlowNodeType("trigger.manual", 1, "trigger", "수동", null, null, MAPPER.createObjectNode(),
                null, List.of(new FlowNodeType.Port("out", "any", null)), null, null, null);
        assertThat(noError.inputs()).isEmpty();
        assertThat(noError.permissions()).isEmpty();
        assertThat(MessageSchemas.validate(MessageSchemas.FLOW_NODE_TYPE, MAPPER.valueToTree(noError))).isNotEmpty();
    }
}
