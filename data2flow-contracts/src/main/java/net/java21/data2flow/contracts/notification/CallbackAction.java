package net.java21.data2flow.contracts.notification;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

import java.util.Optional;

/**
 * 메신저 콜백의 공통 명령(OPS-06.06 {@code handleCallback}, API-RUL-31). 채널이 버튼 응답을 이 값으로 바꾸고, 공통 계층이 core-api
 * 내부 API로 확인·무음·승인을 처리한다. {@code LINK}는 계정 연결 코드 수신(텔레그램 {@code /start {code}})이다.
 */
public enum CallbackAction {
    /** 알람 확인 */
    ACK,
    /** 30분 무음 */
    MUTE_30M,
    /** 승인 요청 승인(플로우 승인 대기 등) */
    APPROVE,
    REJECT,
    /** 계정 연결 코드 */
    LINK,
    @JsonEnumDefaultValue
    UNKNOWN;

    /** {@link ChannelButton#callbackData()} 형식에서 명령을 읽는다. 모르면 빈 값 */
    public static Optional<CallbackAction> fromCallbackData(String data) {
        if (data == null || data.isBlank()) {
            return Optional.empty();
        }
        String name = data.split("\\|", 2)[0];
        for (CallbackAction a : values()) {
            if (a != UNKNOWN && a.name().equals(name)) {
                return Optional.of(a);
            }
        }
        return Optional.empty();
    }
}
