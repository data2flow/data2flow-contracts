package net.java21.data2flow.contracts.flow;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * 플로우 노드 하나(FLW-api §5). <b>노드 ID는 편집기가 처음 만들 때 한 번 정하고 바꾸지 않는다</b>(BR-FLW-01): 노드 상태와 행동 멱등 키의
 * 기준이다.
 *
 * @param id          노드 ID(예: {@code n-thr-1})
 * @param type        노드 종류(예: {@code condition.threshold}, {@code action.control})
 * @param typeVersion 노드 종류 버전. 없으면 1
 * @param name        표시 이름. 없으면 null
 * @param config      노드 설정(종류별 {@code configSchema})
 * @param outputs     동적 출력 포트 이름(예: switch 케이스). 고정 포트면 null
 * @param retry       재시도 설정. 없으면 null
 * @param position    캔버스 위치. 없으면 null
 * @param disabled    끈 노드면 true
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FlowNode(String id, String type, Integer typeVersion, String name, JsonNode config, List<String> outputs,
                       JsonNode retry, Position position, Boolean disabled) {

    public FlowNode {
        if (id == null || id.isBlank() || type == null || type.isBlank()) {
            throw new IllegalArgumentException("노드에는 id와 type이 필요합니다");
        }
        outputs = outputs == null ? null : List.copyOf(outputs);
    }

    /** 캔버스 좌표 */
    public record Position(double x, double y) {
    }
}
