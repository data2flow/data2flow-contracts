package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * EVT-DEV-09 {@code workorder.changed}: 작업 지시 상태 전이(DEV-08.02, 생산 core-api → 소비 action 담당자 알림, analytics 배터리 예측
 * 초기화).
 *
 * @param workOrderId 작업 지시 ID
 * @param type        유형(예: BATTERY_REPLACE·INSPECTION·REPAIR)
 * @param from        이전 상태. 새로 만들었으면 null
 * @param to          새 상태
 * @param deviceIds   대상 기기
 * @param result      완료 결과. 없으면 null
 * @param assigneeId  담당자. 없으면 null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkOrderChanged(long workOrderId, String type, String from, String to, List<Long> deviceIds, String result,
                               Long assigneeId) implements EventPayload {

    public WorkOrderChanged {
        deviceIds = deviceIds == null ? List.of() : List.copyOf(deviceIds);
    }
}
