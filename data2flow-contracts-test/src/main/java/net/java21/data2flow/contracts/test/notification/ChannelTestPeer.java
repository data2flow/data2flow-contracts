package net.java21.data2flow.contracts.test.notification;

import net.java21.data2flow.contracts.notification.InboundRequest;

/**
 * 알림 채널 계약 테스트의 "상대편"(채널 API 서버). 텔레그램 구현은 MockWebServer에 Bot API 응답을 넣고, 가짜 채널은 메모리에서 흉내 낸다.
 * 키트가 결과 분류(성공·일시 실패·영구 실패)와 콜백 변환을 같은 방법으로 시험할 수 있게 한다.
 */
public interface ChannelTestPeer {

    /** 다음 발송에 상대가 줄 응답 */
    enum Reply {
        /** 받음(2xx) */
        OK,
        /** 잠시 실패(429·5xx) */
        TRANSIENT,
        /** 영구 실패(400·401·403 등) */
        PERMANENT
    }

    /** 다음 발송 한 건의 응답을 정한다 */
    void nextReply(Reply reply);

    /** 상대가 실제로 받은(성공 응답한) 발송 수 */
    int delivered();

    /** 올바른 시크릿을 단 콜백 요청(본문은 채널 형식의 버튼 응답: {@code ACK|9001|d-1} 등) */
    InboundRequest callback(String callbackData, String externalUserId);

    /** 시크릿이 틀린 콜백 요청 */
    InboundRequest callbackWithWrongSecret(String callbackData, String externalUserId);

    /** 계정 연결 코드 메시지(텔레그램 {@code /start {code}}) */
    InboundRequest linkStart(String code, String externalUserId);
}
