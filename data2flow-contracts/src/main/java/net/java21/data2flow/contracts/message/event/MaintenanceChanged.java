package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * EVT-OPS-02 {@code ops.maintenance.started}·{@code ops.maintenance.ended}: 유지보수 모드 시작·종료(OPS-05.01·05.02, 생산 core-api →
 * 소비 flow-engine 실행 건너뜀 캐시, action 알림 보류, analytics 제외 구간, core-api SSE 배지). 알람은 SUPPRESSED(MAINTENANCE)로
 * 만든다(BR-RUL-08).
 *
 * @param windowId             유지보수 창 ID
 * @param targetType           SPACE 또는 DEVICE
 * @param targetId             대상 ID
 * @param descendantSpaceIds   대상이 공간이면 하위 공간 ID(펼친 목록). 기기면 빈 목록
 * @param pauseAutomation      그 대상의 자동 제어 플로우를 멈추는가(OPS-05.02)
 * @param excludeFromAnalytics 분석에서 제외하는가
 * @param startsAt             시작 시각
 * @param endsAt               종료(예정) 시각. 정하지 않았으면 null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MaintenanceChanged(long windowId, TargetType targetType, long targetId, List<Long> descendantSpaceIds,
                                 boolean pauseAutomation, boolean excludeFromAnalytics, Instant startsAt, Instant endsAt)
        implements EventPayload {

    public MaintenanceChanged {
        if (targetType == null || startsAt == null) {
            throw new IllegalArgumentException("targetType·startsAt은 필수입니다");
        }
        descendantSpaceIds = descendantSpaceIds == null ? List.of() : List.copyOf(descendantSpaceIds);
    }

    /** 이 공간·기기가 유지보수 대상인가 */
    public boolean covers(Long deviceId, Long spaceId) {
        if (targetType == TargetType.DEVICE) {
            return deviceId != null && deviceId == targetId;
        }
        return spaceId != null && (spaceId == targetId || descendantSpaceIds.contains(spaceId));
    }

    public enum TargetType {
        SPACE, DEVICE,
        @JsonEnumDefaultValue
        UNKNOWN
    }
}
