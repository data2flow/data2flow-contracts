package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * EVT-DEV-01 {@code device.changed}: 기기가 만들어지거나 승인·수정·활성·비활성·삭제·교체되었다(생산 core-api).
 *
 * @param deviceId 기기 ID
 * @param change   바뀐 종류
 * @param fields   수정된 필드 이름(UPDATED일 때)
 * @param status   바뀐 뒤 기기 상태(PENDING·ACTIVE·INACTIVE)
 * @param modelId  기기 모델 ID. 없으면 null
 * @param spaceId  공간 ID. 없으면 null
 * @param version  기기의 새 버전
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeviceChanged(long deviceId, Change change, List<String> fields, String status, String modelId,
                            Long spaceId, long version) implements EventPayload {

    public DeviceChanged {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public enum Change {
        CREATED, APPROVED, UPDATED, ACTIVATED, DEACTIVATED, DELETED, REPLACED
    }
}
