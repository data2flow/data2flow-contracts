package net.java21.data2flow.contracts.notification;

import tools.jackson.databind.JsonNode;

import java.util.Optional;

/**
 * 알림 채널 SPI(OPS-06.06, BR-OPS-32, ADR-033). 알람 알림 채널은 이 인터페이스로만 추가한다. 지금 구현은 텔레그램 하나다.
 *
 * <p>정책·수신자 계산·묶음·재시도·에스컬레이션·아웃박스·멱등 키는 채널과 무관한 공통 계층(action {@code notification} 패키지)이
 * 맡고, 채널 구현은 아래 메서드만 제공한다. 공통 계층은 채널 키로 분기하지 않는다. 버튼을 지원하지 않는 채널에는 공통 계층이
 * {@link ChannelMessage#adaptTo(ChannelCapabilities)}로 버튼 대신 바로가기 링크를 넣는다.
 *
 * <p>구현은 예외를 던지지 않고 결과로 돌려준다({@link SendResult}). 채널 구현은 계약 테스트 키트
 * ({@code data2flow-contracts-test}의 {@code AbstractNotificationChannelContractTest}, TC-OPS-142)를 통과해야 등록한다.
 */
public interface NotificationChannel {

    /** 채널 유형 키(대문자, 예: {@code TELEGRAM}). notification_channels.type, 정책 channels[], 콜백 경로 {@code /hooks/messenger/{소문자 키}} */
    String key();

    /** 이 채널 구현을 쓸 수 있는가. 파사드만 있고 어댑터가 없으면 false("준비 중", API-OPS-34) */
    default boolean available() {
        return true;
    }

    /** 채널 설정 JSON Schema(2020-12). 화면 폼 자동 생성과 저장 검증(OPS-06.06) */
    JsonNode configSchema();

    /** 버튼·서식·길이·기본 한도 */
    ChannelCapabilities capabilities();

    /**
     * 공통 메시지를 채널 형식으로 바꿔 보낸다. 같은 {@link ChannelMessage#idempotencyKey()}로 다시 불려도 상대가 중복을 막을 수
     * 있으면 넘긴다. 결과는 성공·일시 실패(재시도)·영구 실패.
     */
    SendResult send(ChannelSettings settings, ChannelMessage message);

    /** 콜백 요청의 서명·시크릿 검증(텔레그램: {@code X-Telegram-Bot-Api-Secret-Token} 상수 시간 비교). 틀리면 false */
    boolean verify(ChannelSettings settings, InboundRequest request);

    /**
     * 검증된 콜백을 공통 명령으로 바꾼다(버튼 응답 → ACK·MUTE_30M·APPROVE·REJECT, 계정 연결 코드 → LINK). 처리할 것이 없는
     * 요청(일반 대화 등)이면 빈 값.
     */
    Optional<CallbackCommand> handleCallback(ChannelSettings settings, InboundRequest request);

    /** 계정 연결 시작(텔레그램: 봇 {@code /start {일회용 코드}} 딥링크, 10분 유효) */
    LinkResult link(ChannelSettings settings, LinkRequest request);
}
