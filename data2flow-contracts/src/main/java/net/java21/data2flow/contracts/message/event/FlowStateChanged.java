package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * EVT-FLW-03 {@code flow.state.changed}: 엔진이 플로우 상태를 판정해 바꿨다(오류율 상승, 폭주, 순환, 회복). 생산 flow-engine,
 * 소비 core-api(상태 저장·알람), action(알림).
 *
 * @param flowId  플로우 ID
 * @param from    이전 상태
 * @param to      새 상태
 * @param reason  판정 사유
 * @param metrics 판정 근거 지표
 * @param at      판정 시각
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FlowStateChanged(String flowId, String from, String to, Reason reason, Metrics metrics, Instant at)
        implements EventPayload {

    public enum Reason {
        DEGRADED, RUNAWAY, CYCLE, RECOVERED,
        @JsonEnumDefaultValue
        UNKNOWN
    }

    /** @param errorRate 오류 비율(0~1), @param ratePerSec 초당 실행 수 */
    public record Metrics(double errorRate, double ratePerSec) {
    }
}
