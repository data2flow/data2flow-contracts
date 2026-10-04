package net.java21.data2flow.contracts.notification;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 메시지 버튼(텔레그램 인라인 버튼). 누르면 콜백으로 {@code {action, alarmId, deliveryId}}가 돌아온다(API-RUL-31).
 *
 * @param label      표시 문구(수신자 언어)
 * @param action     공통 명령
 * @param alarmId    대상 알람. 없으면 null
 * @param deliveryId 발송 ID(메시지 갱신용)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChannelButton(String label, CallbackAction action, Long alarmId, String deliveryId) {

    public ChannelButton {
        if (label == null || label.isBlank() || action == null) {
            throw new IllegalArgumentException("button.label·action은 필수입니다");
        }
    }

    /** 콜백 데이터 문자열(채널 한도가 작을 때 쓰는 짧은 형식): {@code ACK|9001|d-1} */
    public String callbackData() {
        return action.name() + "|" + (alarmId == null ? "" : alarmId) + "|" + (deliveryId == null ? "" : deliveryId);
    }
}
