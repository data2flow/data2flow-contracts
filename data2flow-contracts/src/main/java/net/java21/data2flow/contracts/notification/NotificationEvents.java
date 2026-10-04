package net.java21.data2flow.contracts.notification;

/**
 * 알림 사건 이름({@link NotificationRequest#event()}). 기본 템플릿 키는 {@code {event}.default}(예: {@code alarm.raised.default},
 * RUL domain-model notification_templates). 목록은 열려 있어 문자열 상수로 둔다.
 */
public final class NotificationEvents {

    public static final String ALARM_RAISED = "alarm.raised";
    public static final String ALARM_RERAISED = "alarm.reraised";
    public static final String ALARM_CLEARED = "alarm.cleared";
    /** 에스컬레이션 단계 발송(BR-RUL-16) */
    public static final String ALARM_ESCALATED = "alarm.escalated";
    /** 플로우 {@code action.notify} 노드(알람과 무관) */
    public static final String FLOW_NOTIFY = "flow.notify";
    /** 운영 알람(EVT-OPS-01) */
    public static final String OPS_ALARM_RAISED = "ops.alarm.raised";
    public static final String OPS_ALARM_CLEARED = "ops.alarm.cleared";

    private NotificationEvents() {
    }

    /** 사건의 기본 템플릿 키 */
    public static String defaultTemplateKey(String event) {
        if (event == null || event.isBlank()) {
            throw new IllegalArgumentException("event가 비어 있습니다");
        }
        return event + ".default";
    }
}
