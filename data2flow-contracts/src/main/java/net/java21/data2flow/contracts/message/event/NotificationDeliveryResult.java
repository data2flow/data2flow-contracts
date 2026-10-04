package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.java21.data2flow.contracts.notification.DeliveryStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * EVT-RUL-04 {@code notification.delivered}·{@code notification.failed}: 알림 발송 결과(생산 action → 소비 core-api 알람 타임라인
 * NOTIFIED, 채널 상태·이력 집계). EVT-OPS-05는 이 이벤트로 합쳐졌다.
 *
 * @param deliveryId 발송 ID
 * @param alarmId    알람. 알람과 무관한 알림이면 null
 * @param channelId  채널 ID(notification_channels.id). WEB이면 null
 * @param channel    채널 키(예: {@code TELEGRAM})
 * @param recipient  수신자 표기(사용자 ID 또는 주소, 비밀값 없음)
 * @param status     SENT·FAILED(그 밖 상태는 이 이벤트로 내지 않는다)
 * @param attempts   시도 횟수
 * @param error      실패 원인. 없으면 null
 * @param at         결과 시각
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationDeliveryResult(UUID deliveryId, Long alarmId, Long channelId, String channel, String recipient,
                                         DeliveryStatus status, int attempts, String error, Instant at)
        implements EventPayload {

    public NotificationDeliveryResult {
        if (deliveryId == null || channel == null || status == null || at == null) {
            throw new IllegalArgumentException("deliveryId·channel·status·at은 필수입니다");
        }
    }
}
