package net.java21.data2flow.contracts.flow;

import net.java21.data2flow.contracts.command.ActionKind;
import net.java21.data2flow.contracts.message.FlowDebugMessage;
import net.java21.data2flow.contracts.message.MessageCodec;
import net.java21.data2flow.contracts.message.MessageFormatException;
import net.java21.data2flow.contracts.message.MessageSchemas;
import net.java21.data2flow.contracts.messaging.MessagingNames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FLW-03·FLW-06: 라이브 편집(상태 정책)·라이브 뷰(디버그 메시지)·실행 추적 계약 */
class LiveReloadContractsTest {

    private static final Instant T = Instant.parse("2026-10-03T01:12:03.120Z");
    private final MessageCodec codec = MessageCodec.create();

    @Test
    @DisplayName("FLW-06.03 TC-FLW-137 BR-FLW-07 상태 정책: 가장 보수적인 것으로 합치고, 모르는 값은 RESET으로 다룬다")
    void statePolicy() {
        assertThat(StatePolicy.strictest(StatePolicy.KEEP, StatePolicy.MIGRATE)).isEqualTo(StatePolicy.MIGRATE);
        assertThat(StatePolicy.strictest(StatePolicy.RESET, StatePolicy.MIGRATE)).isEqualTo(StatePolicy.RESET);
        assertThat(StatePolicy.strictest(null, null)).isEqualTo(StatePolicy.KEEP);
        assertThat(StatePolicy.strictest(StatePolicy.UNKNOWN, StatePolicy.KEEP)).isEqualTo(StatePolicy.RESET);
        assertThat(StatePolicy.KEEP.effective()).isEqualTo(StatePolicy.KEEP);
        assertThat(codec.mapper().readValue("\"SPLIT\"", StatePolicy.class)).isEqualTo(StatePolicy.UNKNOWN);
    }

    @Test
    @DisplayName("FLW-03.01 TC-FLW-066 EVT-FLW-01 노드 카운터 메시지: data2flow.debug 라우팅 키 flow.{flowId}, 스키마 통과, 같은 값으로 읽힘")
    void debugStats() {
        FlowDebugMessage m = FlowDebugMessage.stats("f-7f3a", "data2flow-flow-engine-0", T, 13, List.of(
                new FlowDebugMessage.NodeStats("n-thr-1", 120, Map.of("true", 3L, "false", 117L), 0, T,
                        FlowDebugMessage.NodeStatus.OK, null)));
        MessageSchemas.assertValid(m);
        assertThat(m.routingKey()).isEqualTo("flow.f-7f3a").isEqualTo(MessagingNames.debugRoutingKey("f-7f3a"));
        FlowDebugMessage read = codec.read(codec.write(m), FlowDebugMessage.class);
        assertThat(read).isEqualTo(m);
        JsonNode tree = codec.toTree(m);
        assertThat(tree.get("type").asString()).isEqualTo("node.stats");
        assertThat(tree.at("/stats/0/out/true").asLong()).isEqualTo(3);
        assertThat(MessagingNames.debugQueueArguments()).containsEntry("x-max-length", 1000)
                .containsEntry("x-overflow", "drop-head");
        assertThatThrownBy(() -> MessagingNames.debugRoutingKey(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("FLW-03.02 TC-FLW-062 BR-FLW-12 샘플 메시지: 방향 in·out, 권한 밖이면 masked=true, 샘플이 없으면 형식 오류")
    void debugSample() {
        FlowDebugMessage m = FlowDebugMessage.sample("f-7f3a", "data2flow-flow-engine-0", T, 13,
                new FlowDebugMessage.NodeSample("n-thr-1", "7f3a-msg", FlowDebugMessage.NodeSample.Direction.OUT, "true",
                        null, true));
        MessageSchemas.assertValid(m);
        JsonNode tree = codec.toTree(m);
        assertThat(tree.at("/sample/direction").asString()).isEqualTo("out");
        assertThat(tree.at("/sample/masked").asBoolean()).isTrue();
        assertThat(codec.read(codec.write(m), FlowDebugMessage.class)).isEqualTo(m);
        assertThatThrownBy(() -> new FlowDebugMessage(1, m.messageId(), FlowDebugMessage.Type.NODE_SAMPLE, "f", "i", T, 1, null,
                null)).isInstanceOf(MessageFormatException.class);
        assertThatThrownBy(() -> FlowDebugMessage.routingKey(null)).isInstanceOf(MessageFormatException.class);
        assertThat(new FlowDebugMessage(1, m.messageId(), FlowDebugMessage.Type.UNKNOWN, "f", "i", T, 1, null, null).stats())
                .isNull();
    }

    @Test
    @DisplayName("FLW-03.04 TC-FLW-071 API-FLW-41 Trace 모양: 문서 예시 JSON을 읽고 다시 쓰며 드라이런 행동을 찾는다")
    void traceShape() {
        String json = """
                {"messageId":"7f3a","flowId":"f-7f3a","version":13,"startedAt":"2026-10-03T01:12:03.120Z",
                 "steps":[{"nodeId":"n-trg-1","type":"trigger.telemetry","inMs":0.0,"durationMs":0.2,"input":{"t":1},
                           "outputs":[{"port":"out","payload":{"t":1}}]},
                          {"nodeId":"n-act-1","type":"action.control","durationMs":1.1,"outputs":[{"port":"ok"}],
                           "action":{"kind":"COMMAND","idempotencyKey":"abc","dryRun":true,"skipped":null}}],
                 "result":"COMPLETED","error":null}""";
        FlowTrace trace = codec.mapper().readValue(json, FlowTrace.class);
        assertThat(trace.version()).isEqualTo(13);
        assertThat(trace.steps()).extracting(FlowTrace.Step::nodeId).containsExactly("n-trg-1", "n-act-1");
        assertThat(trace.steps().get(1).action().kind()).isEqualTo(ActionKind.COMMAND);
        assertThat(trace.hasDryRunActions()).isTrue();
        assertThat(trace.result()).isEqualTo(FlowTrace.COMPLETED);
        JsonNode written = codec.mapper().valueToTree(trace);
        assertThat(written.has("error")).isTrue();
        assertThat(written.at("/steps/1/action/dryRun").asBoolean()).isTrue();
        assertThat(codec.mapper().treeToValue(written, FlowTrace.class)).isEqualTo(trace);
        FlowTrace empty = new FlowTrace("m", "f", 1, T, null, FlowTrace.DROPPED, null);
        assertThat(empty.steps()).isEmpty();
        assertThat(empty.hasDryRunActions()).isFalse();
        assertThatThrownBy(() -> new FlowTrace(null, "f", 1, T, null, "X", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FlowTrace.Step(null, "t", null, 0, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
