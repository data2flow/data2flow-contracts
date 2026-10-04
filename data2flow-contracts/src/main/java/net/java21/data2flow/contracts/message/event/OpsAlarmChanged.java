package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.java21.data2flow.contracts.alarm.AlarmSeverity;

import java.time.Instant;

/**
 * EVT-OPS-01 {@code ops.alarm.raised}·{@code ops.alarm.cleared}: 운영 알람(생산 core-api 운영 판정 스케줄러 → 소비 action 알림,
 * core-api SSE, 보내는 Webhook).
 *
 * @param alarmId    운영 알람 ID
 * @param key        판정 키(예: {@code INGEST_ZERO})
 * @param severity   심각도
 * @param component  구성 요소(서비스·저장소 이름)
 * @param value      현재 값
 * @param threshold  기준 값
 * @param occurredAt 판정 시각
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpsAlarmChanged(long alarmId, String key, AlarmSeverity severity, String component, Double value,
                              Double threshold, Instant occurredAt) implements EventPayload {
}
