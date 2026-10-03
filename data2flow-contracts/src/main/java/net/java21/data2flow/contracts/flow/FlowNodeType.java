package net.java21.data2flow.contracts.flow;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * 노드 카탈로그 항목(API-FLW-30 {@code GET /api/v1/core/flow-nodes}, TC-FLW-031). flow-engine 노드 레지스트리가 만들고 core-api가
 * 그대로 내보내며 웹 팔레트·설정 폼이 읽는다. JSON Schema는 {@code flow-node-type.v1.json}.
 *
 * @param type        노드 종류(예: {@code condition.threshold})
 * @param typeVersion 종류 버전
 * @param category    trigger, condition, transform, flow, action, sink, debug, enrich, ai
 * @param name        팔레트 이름
 * @param description 설명
 * @param icon        아이콘 이름
 * @param configSchema 설정 JSON Schema(폼 자동 생성)
 * @param inputs      입력 포트(트리거는 빈 목록)
 * @param outputs     출력 포트(공통 {@code error} 포함)
 * @param statePolicy 설정 필드별 상태 처리(KEEP/RESET/MIGRATE)
 * @param permissions 사용에 필요한 권한(예: 제어 노드는 FLOW_DEPLOY_CONTROL)
 * @param defaults    재시도·시간 제한 기본값
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FlowNodeType(String type, int typeVersion, String category, String name, String description, String icon,
                           JsonNode configSchema, List<Port> inputs, List<Port> outputs, JsonNode statePolicy,
                           List<String> permissions, JsonNode defaults) {

    /** 모든 노드에 있는 오류 출력 포트 */
    public static final String ERROR_PORT = "error";

    public FlowNodeType {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }

    /**
     * 포트.
     *
     * @param name    포트 이름(예: true, false, error)
     * @param type    메시지 종류(예: telemetry, any)
     * @param dynamic 설정으로 개수가 바뀌는 포트면 true
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Port(String name, String type, Boolean dynamic) {
    }
}
