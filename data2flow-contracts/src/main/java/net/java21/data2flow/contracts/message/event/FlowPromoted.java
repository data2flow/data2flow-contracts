package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * EVT-FLW-06 {@code flow.promoted}: 승격 파이프라인 한 단계가 끝났다(FLW-09·11.03, 생산 core-api → 소비 core-api 알림, ai 리포트).
 *
 * @param promotionId     승격 ID
 * @param pipelineId      파이프라인 ID
 * @param snapshotId      스냅샷 ID
 * @param fromStage       이전 단계(예: {@code test})
 * @param toStage         다음 단계(예: {@code prod})
 * @param approvedBy      승인자. 승인이 필요 없으면 null
 * @param appliedVersions 플로우 ID → 적용 버전
 * @param at              완료 시각
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FlowPromoted(long promotionId, long pipelineId, long snapshotId, String fromStage, String toStage,
                           Long approvedBy, Map<String, Integer> appliedVersions, Instant at) implements EventPayload {

    public FlowPromoted {
        appliedVersions = appliedVersions == null ? Map.of() : Map.copyOf(appliedVersions);
    }
}
