package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * EVT-TSD-07 {@code bi.export.completed}·{@code bi.export.failed}: 정기 Parquet·CSV 내보내기(TSD-07.02, 생산 core-api 스케줄 실행기 →
 * 소비 core-api 알림·SSE).
 *
 * @param scheduleId 스케줄 ID
 * @param version    스케줄 버전
 * @param file       올린 파일 경로. 실패면 null
 * @param error      실패 원인. 성공이면 null
 * @param attempts   시도 횟수
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BiExportFinished(long scheduleId, int version, String file, String error, int attempts)
        implements EventPayload {
}
