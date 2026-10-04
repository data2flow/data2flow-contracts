package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * EVT-DEV-13 {@code device.export.completed}: 공간·기기·점 모델 표준 형식 내보내기 끝(DEV-13.04, 생산 core-api → 소비 core-api SSE·알림).
 *
 * @param jobId   작업 ID
 * @param format  DTDL·NGSI_LD
 * @param status  COMPLETED·FAILED
 * @param fileRef 파일 참조. 실패면 null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeviceExportCompleted(String jobId, String format, String status, String fileRef) implements EventPayload {
}
