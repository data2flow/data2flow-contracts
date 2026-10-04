package net.java21.data2flow.contracts.notification;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 콜백을 바꾼 공통 명령. 공통 계층은 {@code externalUserId}로 연결 계정을 찾아(없으면 MESSENGER_NOT_LINKED, BR-RUL-18) 그 사용자의
 * 권한으로 처리한다.
 *
 * @param action            명령
 * @param externalUserId    메신저 사용자 ID(텔레그램 from.id)
 * @param alarmId           대상 알람(ACK·MUTE_30M). 없으면 null
 * @param deliveryId        발송 ID(처리 결과로 같은 메시지를 갱신). 없으면 null
 * @param linkCode          계정 연결 일회용 코드(LINK). 없으면 null
 * @param externalMessageId 버튼이 달린 채널 메시지 ID. 없으면 null
 * @param callbackId        채널에 응답할 콜백 ID(텔레그램 callback_query.id). 없으면 null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CallbackCommand(CallbackAction action, String externalUserId, Long alarmId, String deliveryId,
                              String linkCode, String externalMessageId, String callbackId) {

    public CallbackCommand {
        if (action == null || externalUserId == null || externalUserId.isBlank()) {
            throw new IllegalArgumentException("callback.action·externalUserId는 필수입니다");
        }
        if (action == CallbackAction.LINK && (linkCode == null || linkCode.isBlank())) {
            throw new IllegalArgumentException("LINK 명령에는 linkCode가 필요합니다");
        }
    }

    public static CallbackCommand button(CallbackAction action, String externalUserId, Long alarmId, String deliveryId,
                                         String externalMessageId, String callbackId) {
        return new CallbackCommand(action, externalUserId, alarmId, deliveryId, null, externalMessageId, callbackId);
    }

    public static CallbackCommand link(String externalUserId, String linkCode) {
        return new CallbackCommand(CallbackAction.LINK, externalUserId, null, null, linkCode, null, null);
    }
}
