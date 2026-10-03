package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * EVT-SIM-02 {@code sim.fault.{started|ended}}: 주입한 장애의 정답 라벨(SIM-05.04). 소비: analytics(이상 탐지 평가), core-api(실행 로그).
 * 시각은 모두 시뮬레이션 시각이다.
 *
 * @param organizationId 조직 ID
 * @param runId          실행 ID. 실행 밖 즉시 주입이면 null
 * @param faultId        장애 ID
 * @param kind           장애 종류(STUCK, SPIKE, DRIFT, DROPOUT …)
 * @param targetType     대상 종류(SENSOR, GATEWAY …)
 * @param targetId       대상 ID
 * @param simFrom        시작(시뮬레이션 시각)
 * @param simTo          끝. 진행 중이면 null
 * @param params         장애 매개변수
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SimFaultLabel(long organizationId, Long runId, long faultId, String kind, String targetType, String targetId,
                            Instant simFrom, Instant simTo, Map<String, Object> params) implements EventPayload {
}
