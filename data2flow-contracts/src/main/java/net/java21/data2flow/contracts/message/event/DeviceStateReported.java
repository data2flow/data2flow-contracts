package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * EVT-ACT-07 = EVT-SIM-03 {@code device.state.reported}: 기기 상태 보고(BR-ACT-25). 소비: data2flow-action(desired/reported 비교,
 * APPLIED 판정 → EVT-ACT-02). 버전이 이전보다 클 때만 반영한다(BR-ACT-05).
 *
 * @param deviceId     기기 ID
 * @param version      보고 버전(드라이버 시퀀스 또는 보고 시각 기반)
 * @param capabilities 보고 상태 {@code {capability:{attr:value}}}
 * @param reportedAt   보고 시각
 * @param virtual      가상 장비 보고면 true
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeviceStateReported(long deviceId, long version, Map<String, Map<String, Object>> capabilities,
                                  Instant reportedAt, boolean virtual) implements EventPayload {
}
