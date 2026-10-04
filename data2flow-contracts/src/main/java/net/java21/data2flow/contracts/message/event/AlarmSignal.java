package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonInclude;
import net.java21.data2flow.contracts.alarm.AlarmKeys;
import net.java21.data2flow.contracts.alarm.AlarmSeverity;
import net.java21.data2flow.contracts.alarm.AlarmSourceType;

import java.time.Instant;

/**
 * EVT-RUL-01 {@code alarm.signal}: 알람 발생·해제 요청(생산 flow-engine {@code action.alarm} 노드, 아웃박스 경유 → 소비 core-api 알람
 * 서비스). 알람 서비스는 {@code alarmKey}로 열린 알람을 찾아 만들거나 갱신한다(BR-RUL-02). 중복은 봉투 {@code messageId}로 거른다.
 * 문서 페이로드의 {@code v}·{@code messageId}는 봉투 필드다.
 *
 * @param signal           RAISE 또는 CLEAR
 * @param alarmKey         알람 키({@link AlarmKeys})
 * @param sourceType       출처(규칙이 컴파일된 플로우면 RULE)
 * @param ruleId           규칙 ID(RULE)
 * @param flowId           플로우 ID
 * @param flowVersion      실행한 플로우 버전
 * @param nodeId           알람 노드 ID
 * @param severity         심각도(RAISE 필수)
 * @param title            제목(RAISE 필수)
 * @param deviceId         대상 기기. 공간 알람이면 null
 * @param spaceId          대상 공간
 * @param metric           측정 항목 키
 * @param value            판정 값
 * @param threshold        발생·해제 기준 스냅숏(BR-RUL-04 히스테리시스)
 * @param measuredAt       판정에 쓴 측정 시각
 * @param triggerMessageId 플로우를 깨운 메시지 ID(추적)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AlarmSignal(Signal signal, String alarmKey, AlarmSourceType sourceType, Long ruleId, String flowId,
                          Integer flowVersion, String nodeId, AlarmSeverity severity, String title, Long deviceId,
                          Long spaceId, String metric, Double value, Threshold threshold, Instant measuredAt,
                          String triggerMessageId) implements EventPayload {

    public AlarmSignal {
        if (signal == null || alarmKey == null || alarmKey.isBlank() || sourceType == null || measuredAt == null) {
            throw new IllegalArgumentException("alarm.signal의 signal·alarmKey·sourceType·measuredAt은 필수입니다");
        }
        if (alarmKey.length() > AlarmKeys.MAX_LENGTH) {
            throw new IllegalArgumentException("alarmKey는 200자 이하입니다");
        }
        if (signal == Signal.RAISE && (severity == null || title == null || title.isBlank())) {
            throw new IllegalArgumentException("RAISE에는 severity·title이 필요합니다");
        }
    }

    /** 발생 요청 */
    public static AlarmSignal raise(String alarmKey, AlarmSourceType sourceType, Long ruleId, String flowId, int flowVersion,
                                    String nodeId, AlarmSeverity severity, String title, Long deviceId, Long spaceId,
                                    String metric, Double value, Threshold threshold, Instant measuredAt,
                                    String triggerMessageId) {
        return new AlarmSignal(Signal.RAISE, alarmKey, sourceType, ruleId, flowId, flowVersion, nodeId, severity, title,
                deviceId, spaceId, metric, value, threshold, measuredAt, triggerMessageId);
    }

    /** 해제 요청(조건 해소, auto_clear) */
    public static AlarmSignal clear(String alarmKey, AlarmSourceType sourceType, Long ruleId, String flowId, int flowVersion,
                                    String nodeId, Long deviceId, Long spaceId, String metric, Double value,
                                    Instant measuredAt, String triggerMessageId) {
        return new AlarmSignal(Signal.CLEAR, alarmKey, sourceType, ruleId, flowId, flowVersion, nodeId, null, null,
                deviceId, spaceId, metric, value, null, measuredAt, triggerMessageId);
    }

    public enum Signal {
        RAISE, CLEAR,
        @JsonEnumDefaultValue
        UNKNOWN
    }

    /** 발생·해제 기준. 해제 기준이 없으면 발생 기준을 쓴다(BR-RUL-04) */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Threshold(Double raise, Double clear) {
    }
}
