package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * EVT-DSC-08 {@code source.rotation.progress}: 무중단 자격증명 교체 진행(DSC-07.02, BR-DSC-09: 새 연결 성공 확인 후 이전 연결 해제).
 * 생산 ingress(인스턴스마다) → 소비 core-api(교체 상태 저장, SSE).
 *
 * @param sourceId   소스 ID
 * @param rotationId 교체 작업 ID
 * @param instanceId ingress 인스턴스
 * @param ok         이 인스턴스에서 새 자격증명 연결 성공
 * @param error      실패 원인. 성공이면 null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SourceRotationProgress(long sourceId, String rotationId, String instanceId, boolean ok, String error)
        implements EventPayload {
}
