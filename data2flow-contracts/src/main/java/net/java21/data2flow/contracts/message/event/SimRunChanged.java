package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * EVT-SIM-01 {@code sim.run.{started|paused|resumed|stopped|completed|failed|throttled}}: 시뮬레이션 실행 상태가 바뀌었다(생산 simulator).
 * 소비: core-api(SSE·홈), analytics(정답 라벨 대상 실행).
 *
 * @param organizationId        조직 ID
 * @param runId                 실행 ID
 * @param scenarioId            시나리오 ID. 상시 환경이면 null
 * @param status                실행 상태(CREATED·RUNNING·PAUSED·EVALUATING·COMPLETED·STOPPED·FAILED·PURGED)
 * @param simClock              현재 시뮬레이션 시각
 * @param accelerationEffective 속도 제한 뒤 실제 가속(x1~x60)
 * @param partial               정지된 실행을 끝난 구간까지만 판정했으면 true. 아니면 null
 * @param failureReason         실패 사유. 없으면 null
 * @param at                    실제 시각
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SimRunChanged(long organizationId, long runId, Long scenarioId, String status, Instant simClock,
                            int accelerationEffective, Boolean partial, String failureReason, Instant at)
        implements EventPayload {
}
