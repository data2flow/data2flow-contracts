package net.java21.data2flow.contracts.command;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 명령 출처(ACT-02.03, commands.source jsonb, ActionRequest {@code source}). 종류별로 채우는 칸이 다르다.
 *
 * @param type             출처 종류
 * @param userId           요청 사용자(USER·BULK), 장면·예약을 실행한 사용자
 * @param flowId           플로우 ID(FLOW)
 * @param flowVersion      실행한 플로우 버전(FLOW, 멱등 키에는 넣지 않음 BR-FLW-13)
 * @param nodeId           제어 노드 ID(FLOW)
 * @param triggerMessageId 플로우를 깨운 메시지 ID(FLOW)
 * @param sceneRunId       장면 실행 ID(SCENE)
 * @param scheduleId       예약 ID(SCHEDULE)
 * @param bulkJobId        일괄 작업 ID(BULK)
 * @param suggestionId     AI 제안 ID(AI)
 * @param approvedBy       AI 제안을 승인한 사용자(AI, BR-ACT-15: 승인 없는 AI 명령은 실행하지 않는다)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommandSource(SourceType type, Long userId, String flowId, Integer flowVersion, String nodeId,
                            String triggerMessageId, String sceneRunId, Long scheduleId, String bulkJobId,
                            String suggestionId, Long approvedBy) {

    public CommandSource {
        if (type == null) {
            throw new IllegalArgumentException("source.type은 필수입니다");
        }
    }

    public static CommandSource user(long userId) {
        return new CommandSource(SourceType.USER, userId, null, null, null, null, null, null, null, null, null);
    }

    public static CommandSource flow(String flowId, int flowVersion, String nodeId, String triggerMessageId) {
        return new CommandSource(SourceType.FLOW, null, flowId, flowVersion, nodeId, triggerMessageId, null, null, null,
                null, null);
    }

    public static CommandSource ai(String suggestionId, long approvedBy) {
        return new CommandSource(SourceType.AI, null, null, null, null, null, null, null, null, suggestionId, approvedBy);
    }

    public static CommandSource schedule(long scheduleId) {
        return new CommandSource(SourceType.SCHEDULE, null, null, null, null, null, null, scheduleId, null, null, null);
    }

    public static CommandSource bulk(long userId, String bulkJobId) {
        return new CommandSource(SourceType.BULK, userId, null, null, null, null, null, null, bulkJobId, null, null);
    }

    public static CommandSource system() {
        return new CommandSource(SourceType.SYSTEM, null, null, null, null, null, null, null, null, null, null);
    }

    /** 이 출처의 우선순위(BR-ACT-24). 장면은 {@link CommandPriority#forScene} */
    public CommandPriority priority() {
        return CommandPriority.forSource(type);
    }

    /**
     * 출처 기록에 필요한 칸이 다 있는지(BR-ACT-15). USER·BULK는 userId, FLOW는 flowId·nodeId, AI는 suggestionId·approvedBy,
     * SCHEDULE은 scheduleId, SCENE은 sceneRunId. 빠졌으면 {@link IllegalArgumentException}.
     */
    public CommandSource requireComplete() {
        boolean ok = switch (type) {
            case USER -> userId != null;
            case BULK -> userId != null && bulkJobId != null;
            case FLOW -> flowId != null && nodeId != null;
            case AI -> suggestionId != null && approvedBy != null;
            case SCHEDULE -> scheduleId != null;
            case SCENE -> sceneRunId != null;
            case RULE, SYSTEM -> true;
            case UNKNOWN -> false;
        };
        if (!ok) {
            throw new IllegalArgumentException(type + " 출처에 필요한 값이 없습니다: " + this);
        }
        return this;
    }
}
