package net.java21.data2flow.contracts.notification;

import net.java21.data2flow.contracts.alarm.AlarmSeverity;

import java.util.List;

/**
 * 채널과 무관한 공통 메시지(OPS domain-model §2.3.2 {@code send(ChannelMessage)}). 공통 계층이 템플릿을 채워 만들고 채널은 자기
 * 형식(텔레그램 MarkdownV2 + 인라인 키보드 등)으로만 바꾼다.
 *
 * @param idempotencyKey          발송 멱등 키(notification_deliveries.idempotency_key)
 * @param recipientAddress        채널 주소(텔레그램 chat_id 등)
 * @param title                   제목. 제목이 없는 채널은 본문 첫 줄로 쓴다
 * @param body                    본문(템플릿 결과)
 * @param severity                심각도. 없으면 null
 * @param link                    바로가기 링크. 없으면 null
 * @param buttons                 버튼(ACK·MUTE_30M 등). 버튼을 못 쓰는 채널은 {@link #adaptTo}로 비운다
 * @param locale                  수신자 언어(ko·en·ja·zh)
 * @param replaceExternalMessageId 이전에 보낸 메시지를 갱신할 때 그 채널 메시지 ID(버튼 응답 후 갱신, BR-RUL-18). 새 메시지면 null
 */
public record ChannelMessage(String idempotencyKey, String recipientAddress, String title, String body,
                             AlarmSeverity severity, String link, List<ChannelButton> buttons, String locale,
                             String replaceExternalMessageId) {

    /** 잘린 본문 끝 표시 */
    public static final String ELLIPSIS = "…";

    public ChannelMessage {
        if (idempotencyKey == null || idempotencyKey.isBlank() || recipientAddress == null || recipientAddress.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey·recipientAddress는 필수입니다");
        }
        body = body == null ? "" : body;
        buttons = buttons == null ? List.of() : List.copyOf(buttons);
    }

    /**
     * 채널 기능에 맞춘다(BR-OPS-32): 버튼을 못 쓰면 버튼을 빼고 링크가 본문에 없으면 끝에 붙이며, 본문이 최대 길이를 넘으면 자르고
     * {@value #ELLIPSIS}를 붙인다(링크는 남긴다).
     */
    public ChannelMessage adaptTo(ChannelCapabilities capabilities) {
        List<ChannelButton> keptButtons = capabilities.buttons() ? buttons : List.of();
        String suffix = "";
        if (!capabilities.buttons() && link != null && !body.contains(link)) {
            suffix = "\n" + link;
        }
        int max = capabilities.maxBodyLength();
        String text = body;
        if (text.length() + suffix.length() > max) {
            int room = Math.max(0, max - suffix.length() - ELLIPSIS.length());
            text = text.substring(0, Math.min(room, text.length())) + ELLIPSIS;
        }
        return new ChannelMessage(idempotencyKey, recipientAddress, title, text + suffix, severity, link, keptButtons,
                locale, replaceExternalMessageId);
    }
}
