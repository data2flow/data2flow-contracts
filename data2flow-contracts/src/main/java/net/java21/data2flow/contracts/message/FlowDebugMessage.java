package net.java21.data2flow.contracts.message;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 라이브 뷰 디버그 메시지 v1(EVT-FLW-01, FLW-03.01~03.03): topic exchange {@code data2flow.debug}, 라우팅 키 {@code flow.{flowId}}
 * ({@link #routingKey()}). 생산 flow-engine(노드 카운터 1초 집계, 샘플은 노드당 초당 5건·디버그 노드 50건, BR-FLW-12) → 소비 core-api
 * (열린 편집기의 flowId만 바인딩한 임시 큐, {@code x-max-length} 1000·drop-head, 손실 허용) → WebSocket(API-FLW-40).
 *
 * <p>core-api는 {@code type}과 {@code stats}(또는 {@code sample})를 펼쳐 API-FLW-40 서버 메시지({@code node.stats}·{@code node.sample})
 * 로 보낸다. {@code instanceId}는 어느 엔진 인스턴스가 보냈는지(여러 인스턴스의 카운터를 합칠 때)다.
 *
 * @param v          스키마 버전(1)
 * @param messageId  디버그 메시지 ID(중복 제거용, 플로우 메시지 ID와 다름)
 * @param type       {@code node.stats} 또는 {@code node.sample}
 * @param flowId     플로우 ID
 * @param instanceId 엔진 인스턴스
 * @param t          집계·샘플 시각
 * @param version    실행 중인 플로우 버전
 * @param stats      (node.stats) 노드별 카운터
 * @param sample     (node.sample) 샘플 메시지
 */
@MessageSchema(name = "flow-debug", version = 1)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FlowDebugMessage(int v, UUID messageId, Type type, String flowId, String instanceId, Instant t, int version,
                               List<NodeStats> stats, NodeSample sample) implements Message {

    public static final int VERSION = 1;
    /** 라우팅 키 접두사 */
    public static final String ROUTING_KEY_PREFIX = "flow.";

    public FlowDebugMessage {
        Messages.requireVersion(v);
        Messages.require(messageId, "messageId");
        Messages.require(type, "type");
        Messages.requireText(flowId, "flowId");
        Messages.requireText(instanceId, "instanceId");
        Messages.require(t, "t");
        if (type == Type.NODE_STATS) {
            stats = Messages.list(stats);
        } else if (type == Type.NODE_SAMPLE) {
            Messages.require(sample, "sample");
        }
    }

    public static FlowDebugMessage stats(String flowId, String instanceId, Instant t, int version, List<NodeStats> nodes) {
        return new FlowDebugMessage(VERSION, UUID.randomUUID(), Type.NODE_STATS, flowId, instanceId, t, version, nodes, null);
    }

    public static FlowDebugMessage sample(String flowId, String instanceId, Instant t, int version, NodeSample sample) {
        return new FlowDebugMessage(VERSION, UUID.randomUUID(), Type.NODE_SAMPLE, flowId, instanceId, t, version, null, sample);
    }

    /** {@code data2flow.debug} 라우팅 키 */
    @JsonIgnore
    public String routingKey() {
        return routingKey(flowId);
    }

    public static String routingKey(String flowId) {
        Messages.requireText(flowId, "flowId");
        return ROUTING_KEY_PREFIX + flowId;
    }

    public enum Type {
        @JsonProperty("node.stats") NODE_STATS,
        @JsonProperty("node.sample") NODE_SAMPLE,
        @JsonEnumDefaultValue
        UNKNOWN
    }

    /** 노드 상태 배지(FLW-03.03) */
    public enum NodeStatus {
        OK, WARN, ERROR,
        @JsonEnumDefaultValue
        UNKNOWN
    }

    /**
     * 노드 카운터(1초).
     *
     * @param nodeId    노드
     * @param in        들어온 메시지 수
     * @param out       포트 → 나간 수
     * @param errors    오류 수
     * @param lastAt    마지막 처리 시각
     * @param status    상태 배지
     * @param lastError 마지막 오류 문구. 없으면 null
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NodeStats(String nodeId, long in, Map<String, Long> out, long errors, Instant lastAt, NodeStatus status,
                            String lastError) {

        public NodeStats {
            Messages.requireText(nodeId, "nodeId");
            out = Messages.map(out);
        }
    }

    /**
     * 샘플 메시지.
     *
     * @param nodeId    노드
     * @param messageId 플로우 메시지 ID(추적 API-FLW-41로 이어짐)
     * @param direction 들어온 것(in)인지 나간 것(out)인지
     * @param port      나간 포트(out). 없으면 null
     * @param payload   값. 권한 밖 공간이면 core-api가 가린다
     * @param masked    가렸는가
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NodeSample(String nodeId, String messageId, Direction direction, String port, JsonNode payload,
                             boolean masked) {

        public NodeSample {
            Messages.requireText(nodeId, "nodeId");
            Messages.require(direction, "direction");
        }

        public enum Direction {
            @JsonProperty("in") IN,
            @JsonProperty("out") OUT
        }
    }
}
