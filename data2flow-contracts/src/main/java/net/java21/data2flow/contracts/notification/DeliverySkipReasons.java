package net.java21.data2flow.contracts.notification;

/** SKIPPED 사유(notification_deliveries.skip_reason). 화면은 {@code SKIPPED_DND}처럼 붙여 표기한다 */
public final class DeliverySkipReasons {

    public static final String SILENCED = "SILENCED";
    public static final String DND = "DND";
    public static final String FLAPPING = "FLAPPING";
    public static final String SUPPRESSED = "SUPPRESSED";
    public static final String RENOTIFY_INTERVAL = "RENOTIFY_INTERVAL";
    /** 가상 기기 알림 발송 끄기(SIM-07.04) */
    public static final String VIRTUAL_MUTED = "VIRTUAL_MUTED";

    private DeliverySkipReasons() {
    }
}
