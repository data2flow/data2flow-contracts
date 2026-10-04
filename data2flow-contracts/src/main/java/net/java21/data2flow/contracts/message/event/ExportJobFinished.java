package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * EVT-TSD-01 {@code export.completed}·{@code export.failed}: 데이터 내보내기 작업 끝(TSD-04.01·04.03, 생산 core-api → 소비 core-api
 * SSE·알림 센터, action 정기 내보내기 메일).
 *
 * @param jobId       작업 ID
 * @param requestedBy 요청 사용자. 정기 작업이면 null
 * @param status      COMPLETED 또는 FAILED
 * @param rows        행 수
 * @param downloadUrl 내려받기 경로(서명된 단기 URL). 실패면 null
 * @param error       실패 원인. 성공이면 null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExportJobFinished(String jobId, Long requestedBy, String status, long rows, String downloadUrl, String error)
        implements EventPayload {
}
