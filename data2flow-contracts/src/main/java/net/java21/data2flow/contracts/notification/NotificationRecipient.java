package net.java21.data2flow.contracts.notification;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 알림 수신자(EVT-RUL-03 {@code recipients[]}).
 *
 * @param type    수신자 종류
 * @param id      USER는 사용자 ID, ROLE은 역할 이름, ON_CALL·CHANNEL_DEFAULT는 null
 * @param channel 채널 키(SPI {@link NotificationChannel#key()}, 예: {@code TELEGRAM}) 또는 {@code WEB}
 * @param address 채널 주소(텔레그램 chat_id 등)를 이미 안다면. 없으면 action이 연결 계정·채널 기본값으로 찾는다
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationRecipient(Type type, String id, String channel, String address) {

    /** 웹 알림 센터(채널 SPI가 아닌 내장 채널) */
    public static final String WEB = "WEB";

    public NotificationRecipient {
        if (type == null || channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("recipient.type·channel은 필수입니다");
        }
        if ((type == Type.USER || type == Type.ROLE) && (id == null || id.isBlank())) {
            throw new IllegalArgumentException(type + " 수신자에는 id가 필요합니다");
        }
    }

    public static NotificationRecipient user(long userId, String channel) {
        return new NotificationRecipient(Type.USER, Long.toString(userId), channel, null);
    }

    public static NotificationRecipient onCall(String channel) {
        return new NotificationRecipient(Type.ON_CALL, null, channel, null);
    }

    /** 채널에 등록된 기본 대화방(텔레그램 기본 chat_id 목록) */
    public static NotificationRecipient channelDefault(String channel) {
        return new NotificationRecipient(Type.CHANNEL_DEFAULT, null, channel, null);
    }

    /** 멱등 키에 쓰는 수신자 표기: {@code USER:5}, {@code ON_CALL}, {@code CHANNEL_DEFAULT:-100123}(주소가 있으면 주소) */
    public String recipientKey() {
        String base = id == null ? type.name() : type.name() + ":" + id;
        return address == null ? base : base + "@" + address;
    }

    public enum Type {
        USER, ROLE, ON_CALL, CHANNEL_DEFAULT,
        @JsonEnumDefaultValue
        UNKNOWN
    }
}
