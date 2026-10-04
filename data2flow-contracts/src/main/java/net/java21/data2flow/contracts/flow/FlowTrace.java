package net.java21.data2flow.contracts.flow;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.java21.data2flow.contracts.command.ActionKind;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

/**
 * 실행 추적(API-FLW-41 Trace, FLW-03.04). 메시지 하나가 플로우를 지나며 거친 노드·포트·행동을 한 버전 기준으로 남긴다(BR-FLW-06: 한
 * 메시지는 한 버전). flow-engine이 만들고(내부 API {@code /internal/flow/traces/{message-id}}, 시험 실행 API-FLW-12 응답), core-api가
 * 중계하고, 웹이 그린다. 디버그 노드가 켜졌거나 샘플된 메시지만 1시간 보관한다.
 *
 * @param messageId 플로우 메시지 ID
 * @param flowId    플로우 ID
 * @param version   처리한 플로우 버전
 * @param startedAt 처리 시작 시각
 * @param steps     거친 노드(순서대로)
 * @param result    실행 결과({@link #COMPLETED} 등)
 * @param error     실패 정보. 없으면 null
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record FlowTrace(String messageId, String flowId, int version, Instant startedAt, List<Step> steps, String result,
                        JsonNode error) {

    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";
    /** 노드 수 한도(BR-FLW-16: 메시지당 100개)에 걸림 */
    public static final String HOP_LIMIT = "HOP_LIMIT";
    public static final String DROPPED = "DROPPED";

    public FlowTrace {
        if (messageId == null || flowId == null || startedAt == null || result == null) {
            throw new IllegalArgumentException("messageId·flowId·startedAt·result는 필수입니다");
        }
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    /** 드라이런(시험 실행·재생)에서 나간 행동이 하나라도 있는가 */
    public boolean hasDryRunActions() {
        return steps.stream().anyMatch(s -> s.action() != null && s.action().dryRun());
    }

    /**
     * 노드 한 번 실행.
     *
     * @param nodeId     노드 ID
     * @param type       노드 종류
     * @param inMs       처리 시작부터 이 노드에 들어온 시각(ms)
     * @param durationMs 노드 처리 시간(ms)
     * @param input      입력(권한 밖이면 가린 값). 없으면 null
     * @param outputs    나간 포트와 값
     * @param action     행동 노드면 행동 기록
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Step(String nodeId, String type, Double inMs, double durationMs, JsonNode input, List<Output> outputs,
                       ActionRecord action) {

        public Step {
            if (nodeId == null || type == null) {
                throw new IllegalArgumentException("step.nodeId·type은 필수입니다");
            }
            outputs = outputs == null ? List.of() : List.copyOf(outputs);
        }
    }

    /** 나간 포트와 값(값이 없으면 null) */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Output(String port, JsonNode payload) {
    }

    /**
     * 행동 기록.
     *
     * @param kind           행동 종류
     * @param idempotencyKey 행동 멱등 키(BR-FLW-13)
     * @param dryRun         드라이런이면 true(실제로 나가지 않음)
     * @param skipped        건너뛴 사유(바이패스·비상 정지·유지보수 등). 나갔으면 null
     */
    public record ActionRecord(ActionKind kind, String idempotencyKey, boolean dryRun, String skipped) {
    }
}
