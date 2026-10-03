package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * EVT-FLW-02 {@code flow.apply.reported}: 엔진 인스턴스 하나가 플로우 버전 적용 결과를 보고했다(생산 flow-engine, 소비 core-api).
 * flow-engine은 이 이벤트를 내기 전에 {@code flow_instance_versions}를 직접 갱신한다.
 *
 * @param flowId          플로우 ID
 * @param instanceId      엔진 인스턴스(파드) ID
 * @param appliedVersion  적용한 버전(실패하면 유지 중인 이전 버전)
 * @param overlayRevision 적용한 오버레이 리비전. 없으면 null
 * @param compileMs       컴파일 시간(ms)
 * @param error           컴파일·적용 실패 사유. 성공이면 null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FlowApplyReported(String flowId, String instanceId, int appliedVersion, Long overlayRevision, long compileMs,
                                String error) implements EventPayload {
}
