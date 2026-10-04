package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * EVT-ING-09 {@code ingest.reprocess.finished}: 기간 재처리 작업이 끝났다(ING-01.04, BR-ING-12·13). 생산 pipeline(재처리 소비자) →
 * 소비 core-api(감사 {@code REPROCESS_COMPLETED}, SSE {@code ingest.reprocess} 마지막 상태, 알림 센터). 진행률은 내부 API·SSE로 본다.
 * 재처리로 바뀐 집계 구간은 EVT-TSD-03 {@code aggregates.recomputed}로 따로 알린다.
 *
 * @param jobId       작업 ID
 * @param status      COMPLETED·FAILED·CANCELLED
 * @param sourceId    대상 소스. 없으면 null
 * @param deviceIds   대상 기기(비어 있으면 소스 전체)
 * @param from        구간 시작
 * @param to          구간 끝
 * @param total       대상 원본 수
 * @param processed   처리 성공
 * @param failed      실패
 * @param skipped     같은 결과라 건너뜀
 * @param requestedBy 요청 사용자
 * @param error       실패 원인. 없으면 null
 * @param finishedAt  끝난 시각
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReprocessJobFinished(String jobId, Status status, Long sourceId, List<Long> deviceIds, Instant from, Instant to,
                                   long total, long processed, long failed, long skipped, Long requestedBy, String error,
                                   Instant finishedAt) implements EventPayload {

    public ReprocessJobFinished {
        if (jobId == null || status == null || from == null || to == null || finishedAt == null) {
            throw new IllegalArgumentException("jobId·status·from·to·finishedAt은 필수입니다");
        }
        deviceIds = deviceIds == null ? List.of() : List.copyOf(deviceIds);
    }

    public enum Status {
        COMPLETED, FAILED, CANCELLED,
        @JsonEnumDefaultValue
        UNKNOWN
    }
}
