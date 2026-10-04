package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * EVT-DEV-14 {@code device.commissioning.changed}: QR 현장 설치 상태가 바뀌었다(DEV-13.05·13.06, 생산 core-api → 소비 core-api SSE
 * UI-DEV-21·22).
 *
 * @param deviceId    기기
 * @param status      설치 상태(PLANNED·INSTALLED·FIRST_SEEN·PROBLEM)
 * @param firstSeenAt 첫 수신 시각. 아직이면 null
 * @param checklist   체크리스트 항목 → 통과 여부. 없으면 null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeviceCommissioningChanged(long deviceId, String status, Instant firstSeenAt, Map<String, Boolean> checklist)
        implements EventPayload {

    public DeviceCommissioningChanged {
        checklist = checklist == null ? null : Map.copyOf(checklist);
    }
}
