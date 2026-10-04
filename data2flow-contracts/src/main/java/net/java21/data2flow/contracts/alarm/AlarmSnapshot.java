package net.java21.data2flow.contracts.alarm;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * 알람 한 건의 모양(API-RUL-10 {@code Alarm} 형식). EVT-RUL-02 {@code alarm.*} 페이로드의 {@code alarm}이고 SSE(API-RUL-14)도 같은 모양이다.
 * 이벤트에서는 이름 칸(기기 이름·공간 경로·사용자 이름)을 비워도 된다(소비자가 필요하면 다시 읽는다).
 *
 * @param id               알람 ID
 * @param alarmKey         알람 키({@link AlarmKeys})
 * @param severity         심각도
 * @param status           상태
 * @param flapping         플래핑 중(BR-RUL-10)
 * @param title            제목
 * @param source           출처
 * @param device           대상 기기. 공간 알람이면 null
 * @param space            대상 공간. 없으면 null
 * @param metric           측정 항목 키. 없으면 null
 * @param triggerValue     발생 값
 * @param peakValue        최고(최악) 값
 * @param lastValue        마지막 값
 * @param occurrenceCount  발생 횟수(재발생마다 +1, BR-RUL-02)
 * @param raisedAt         처음 발생 시각
 * @param lastRaisedAt     마지막 발생 시각
 * @param ackedBy          확인한 사용자
 * @param ackedAt          확인 시각
 * @param clearedAt        해제 시각
 * @param clearReason      해제 사유
 * @param assignee         담당자
 * @param parentAlarmId    상위 알람(토폴로지 묶기, RUL-04.01)
 * @param childCount       하위 알람 수
 * @param spaceEventId     같은 원인 묶음(RUL-04.03)
 * @param suppressedReason 억제 사유
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AlarmSnapshot(long id, String alarmKey, AlarmSeverity severity, AlarmStatus status, boolean flapping,
                            String title, Source source, Ref device, SpaceRef space, String metric, Double triggerValue,
                            Double peakValue, Double lastValue, int occurrenceCount, Instant raisedAt, Instant lastRaisedAt,
                            UserRef ackedBy, Instant ackedAt, Instant clearedAt, AlarmClearReason clearReason,
                            UserRef assignee, Long parentAlarmId, Integer childCount, Long spaceEventId,
                            SuppressedReason suppressedReason) {

    public AlarmSnapshot {
        if (id < 1) {
            throw new IllegalArgumentException("alarm.id는 1 이상입니다");
        }
        if (severity == null || status == null || source == null || raisedAt == null) {
            throw new IllegalArgumentException("alarm.severity·status·source·raisedAt은 필수입니다");
        }
    }

    /**
     * 출처.
     *
     * @param type   출처 종류
     * @param ruleId 규칙 ID(RULE)
     * @param flowId 플로우 ID(RULE은 컴파일된 플로우, FLOW)
     * @param nodeId 알람 노드 ID
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Source(AlarmSourceType type, Long ruleId, String flowId, String nodeId) {
    }

    /** 기기 참조(이름은 선택) */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Ref(long id, String name) {
    }

    /** 공간 참조. {@code path}는 끝 '/' 없는 ID 경로(예: {@code /1/7/31}) */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SpaceRef(long id, String path) {
    }

    /** 사용자 참조 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UserRef(long userId, String name) {
    }
}
