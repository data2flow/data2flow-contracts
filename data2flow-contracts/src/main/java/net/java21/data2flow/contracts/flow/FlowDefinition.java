package net.java21.data2flow.contracts.flow;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 플로우 정의 v1(FLW-api §5 {@code flow-definition.schema.json}, flow-engine-and-live-reload.md §2.1, flows.definition jsonb ≤ 2MB).
 * core-api는 저장 전 검증에, flow-engine은 실행 계획 컴파일에, 웹 캔버스는 편집에 같은 모양을 쓴다. JSON Schema는
 * {@code classpath:data2flow/contracts/schemas/flow-definition.v1.json}.
 *
 * <p>노드 설정({@code config})의 모양은 노드 종류마다 다르므로 여기서는 검사하지 않는다(노드 카탈로그 {@link FlowNodeType#configSchema()}).
 * 오버레이(바이패스·디버그)는 버전을 올리지 않는 실행 옵션이라 정의에 넣지 않는다(FLW-06.04).
 *
 * @param schema    스키마 표시 {@value #SCHEMA}
 * @param mode      실행 모드(동시 실행 방식)
 * @param variables 플로우 변수
 * @param nodes     노드
 * @param wires     연결선
 * @param subflows  사용하는 서브플로우와 버전
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FlowDefinition(String schema, Mode mode, List<Variable> variables, List<FlowNode> nodes, List<Wire> wires,
                             List<SubflowRef> subflows) {

    public static final String SCHEMA = "data2flow.flow-definition/v1";
    /** 저장 크기 상한(바이트, FLW domain-model flows.definition) */
    public static final int MAX_BYTES = 2 * 1024 * 1024;

    public FlowDefinition {
        variables = variables == null ? List.of() : List.copyOf(variables);
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        wires = wires == null ? List.of() : List.copyOf(wires);
        subflows = subflows == null ? List.of() : List.copyOf(subflows);
    }

    /**
     * 구조 검사(스키마로 표현하기 어려운 것): 스키마 표시, 노드 ID 중복, 없는 노드를 잇는 연결선, 자기 자신으로 가는 연결선, 변수 이름 중복.
     * 문제 설명 목록을 돌려주고 비어 있으면 통과다. 노드 설정 검증은 엔진의 노드 레지스트리가 한다.
     */
    public List<String> structuralErrors() {
        List<String> errors = new ArrayList<>();
        if (!SCHEMA.equals(schema)) {
            errors.add("schema는 " + SCHEMA + "여야 합니다: " + schema);
        }
        Set<String> ids = new HashSet<>();
        for (FlowNode n : nodes) {
            if (!ids.add(n.id())) {
                errors.add("노드 ID가 겹칩니다: " + n.id());
            }
        }
        for (Wire w : wires) {
            if (!ids.contains(w.from())) {
                errors.add("연결선의 시작 노드가 없습니다: " + w.from());
            }
            if (!ids.contains(w.to())) {
                errors.add("연결선의 끝 노드가 없습니다: " + w.to());
            }
            if (w.from().equals(w.to())) {
                errors.add("노드가 자기 자신으로 이어집니다: " + w.from());
            }
        }
        Set<String> names = new HashSet<>();
        for (Variable v : variables) {
            if (!names.add(v.name())) {
                errors.add("변수 이름이 겹칩니다: " + v.name());
            }
        }
        return errors;
    }

    /**
     * 실행 모드.
     *
     * @param concurrency 동시 실행 방식(예: queued)
     * @param keyBy       순서를 지킬 키(예: deviceId)
     * @param max         최대 동시 실행 수
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Mode(String concurrency, String keyBy, Integer max) {
    }

    /** 플로우 변수 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Variable(String name, String type, JsonNode initial) {
    }

    /**
     * 연결선.
     *
     * @param from 시작 노드 ID
     * @param port 시작 노드의 출력 포트. 기본 출력이면 null
     * @param to   끝 노드 ID
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Wire(String from, String port, String to) {
        public Wire {
            if (from == null || from.isBlank() || to == null || to.isBlank()) {
                throw new IllegalArgumentException("연결선에는 from과 to가 필요합니다");
            }
        }
    }

    /** 서브플로우 참조 */
    public record SubflowRef(String id, int version) {
    }
}
