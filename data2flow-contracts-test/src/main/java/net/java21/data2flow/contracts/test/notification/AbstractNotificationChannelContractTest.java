package net.java21.data2flow.contracts.test.notification;

import net.java21.data2flow.contracts.alarm.AlarmSeverity;
import net.java21.data2flow.contracts.message.MessageCodec;
import net.java21.data2flow.contracts.message.MessageSchemas;
import net.java21.data2flow.contracts.notification.CallbackAction;
import net.java21.data2flow.contracts.notification.CallbackCommand;
import net.java21.data2flow.contracts.notification.ChannelButton;
import net.java21.data2flow.contracts.notification.ChannelCapabilities;
import net.java21.data2flow.contracts.notification.ChannelMessage;
import net.java21.data2flow.contracts.notification.ChannelSettings;
import net.java21.data2flow.contracts.notification.LinkRequest;
import net.java21.data2flow.contracts.notification.LinkResult;
import net.java21.data2flow.contracts.notification.NotificationChannel;
import net.java21.data2flow.contracts.notification.SendResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 알림 채널 계약 테스트 키트(OPS-06.06, BR-OPS-32, TC-OPS-142). 모든 {@link NotificationChannel} 구현은 이 키트를 통과해야 등록한다
 * (지금은 텔레그램, 테스트용 {@link FakeNotificationChannel}).
 *
 * <p>action은 채널마다 이 클래스를 상속한 {@code *ChannelContractTest}를 만들고 {@link #channel()}, {@link #settings()},
 * {@link #peer()}, {@link #recipientAddress()}를 채운다(텔레그램은 MockWebServer에 Bot API 응답). 키트가 확인하는 시나리오:
 * <ol>
 *   <li>키(대문자)·설정 스키마(유효한 JSON Schema)·기능 정보</li>
 *   <li>send 결과 분류: 성공(채널 메시지 ID), 일시 실패(재시도), 영구 실패 — 예외를 던지지 않는다</li>
 *   <li>버튼을 못 쓰는 채널도 공통 계층이 맞춘 메시지({@link ChannelMessage#adaptTo})를 보낼 수 있다</li>
 *   <li>verify: 올바른 시크릿만 통과(상수 시간 비교는 구현 책임)</li>
 *   <li>handleCallback: 버튼 응답 → ACK·MUTE_30M, 계정 연결 코드 → LINK, 처리할 것이 없으면 빈 값</li>
 *   <li>link: 딥링크와 만료 시각</li>
 * </ol>
 */
public abstract class AbstractNotificationChannelContractTest {

    /** 시험할 채널 */
    protected abstract NotificationChannel channel();

    /** 테스트 상대에 맞춘 채널 설정 */
    protected abstract ChannelSettings settings();

    /** 테스트 상대 */
    protected abstract ChannelTestPeer peer();

    /** 수신 주소(텔레그램 chat_id 등) */
    protected abstract String recipientAddress();

    protected ChannelMessage message() {
        return new ChannelMessage(UUID.randomUUID().toString(), recipientAddress(), "실습실 고온",
                "실습실 온도 27.6℃ (기준 27℃, 5분 지속)", AlarmSeverity.MAJOR, "https://data2flow.java21.net/alarms/9001",
                List.of(new ChannelButton("확인", CallbackAction.ACK, 9001L, "d-1"),
                        new ChannelButton("30분 무음", CallbackAction.MUTE_30M, 9001L, "d-1")), "ko", null);
    }

    @Test
    @DisplayName("OPS-06.06 TC-OPS-142 채널 키·설정 스키마(유효한 JSON Schema)·기능 정보가 완전하다")
    void descriptorIsComplete() {
        NotificationChannel ch = channel();
        assertThat(ch.key()).matches("[A-Z][A-Z0-9_]{1,19}");
        JsonNode schema = ch.configSchema();
        assertThat(schema).isNotNull();
        assertThat(schema.path("type").asString("")).isEqualTo("object");
        // 스키마로 컴파일되고 빈 객체를 검사할 수 있어야 한다(화면 폼 자동 생성의 전제)
        MessageSchemas.validateWithSchema(schema, MessageCodec.create().mapper().createObjectNode());
        ChannelCapabilities caps = ch.capabilities();
        assertThat(caps.maxBodyLength()).isPositive();
        assertThat(caps.defaultRatePerMin()).isPositive();
        assertThat(caps.formats()).isNotEmpty();
    }

    @Test
    @DisplayName("OPS-06.06 TC-OPS-142 send 성공: 상대가 받고 채널 메시지 ID를 돌려준다")
    void sendSucceeds() {
        int before = peer().delivered();
        peer().nextReply(ChannelTestPeer.Reply.OK);
        SendResult r = channel().send(settings(), message().adaptTo(channel().capabilities()));
        assertThat(r.outcome()).isEqualTo(SendResult.Outcome.SUCCESS);
        assertThat(r.externalMessageId()).isNotBlank();
        assertThat(peer().delivered()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("OPS-06.03 TC-OPS-142 send 일시 실패(429·5xx)는 재시도 대상, 영구 실패는 아니다 — 예외 없이 결과로")
    void sendFailuresAreClassified() {
        peer().nextReply(ChannelTestPeer.Reply.TRANSIENT);
        SendResult transientResult = channel().send(settings(), message().adaptTo(channel().capabilities()));
        assertThat(transientResult.outcome()).isEqualTo(SendResult.Outcome.TRANSIENT_FAILURE);
        assertThat(transientResult.retryable()).isTrue();
        assertThat(transientResult.error()).isNotBlank();

        peer().nextReply(ChannelTestPeer.Reply.PERMANENT);
        SendResult permanent = channel().send(settings(), message().adaptTo(channel().capabilities()));
        assertThat(permanent.outcome()).isEqualTo(SendResult.Outcome.PERMANENT_FAILURE);
        assertThat(permanent.retryable()).isFalse();
    }

    @Test
    @DisplayName("OPS-06.06 TC-OPS-141 BR-OPS-32 버튼을 못 쓰는 채널은 버튼 없이 링크가 든 본문을 받는다")
    void buttonlessMessageIsAccepted() {
        ChannelMessage adapted = message().adaptTo(channel().capabilities());
        if (!channel().capabilities().buttons()) {
            assertThat(adapted.buttons()).isEmpty();
            assertThat(adapted.body()).contains("https://data2flow.java21.net/alarms/9001");
        }
        assertThat(adapted.body().length()).isLessThanOrEqualTo(channel().capabilities().maxBodyLength());
        peer().nextReply(ChannelTestPeer.Reply.OK);
        assertThat(channel().send(settings(), adapted).outcome()).isEqualTo(SendResult.Outcome.SUCCESS);
    }

    @Test
    @DisplayName("RUL-05.02 TC-OPS-142 verify: 시크릿이 틀린 콜백은 거부하고 올바른 콜백만 통과한다")
    void verifyRejectsWrongSecret() {
        assertThat(channel().verify(settings(), peer().callback("ACK|9001|d-1", "777"))).isTrue();
        assertThat(channel().verify(settings(), peer().callbackWithWrongSecret("ACK|9001|d-1", "777"))).isFalse();
    }

    @Test
    @DisplayName("RUL-05.02 TC-OPS-142 handleCallback: 버튼 응답 → ACK·MUTE_30M(알람·발송 ID 포함), 연결 코드 → LINK")
    void callbacksBecomeCommonCommands() {
        Optional<CallbackCommand> ack = channel().handleCallback(settings(), peer().callback("ACK|9001|d-1", "777"));
        assertThat(ack).isPresent();
        assertThat(ack.get().action()).isEqualTo(CallbackAction.ACK);
        assertThat(ack.get().alarmId()).isEqualTo(9001L);
        assertThat(ack.get().deliveryId()).isEqualTo("d-1");
        assertThat(ack.get().externalUserId()).isEqualTo("777");

        Optional<CallbackCommand> mute = channel().handleCallback(settings(), peer().callback("MUTE_30M|9001|d-1", "777"));
        assertThat(mute).map(CallbackCommand::action).contains(CallbackAction.MUTE_30M);

        Optional<CallbackCommand> link = channel().handleCallback(settings(), peer().linkStart("ABC123", "777"));
        assertThat(link).isPresent();
        assertThat(link.get().action()).isEqualTo(CallbackAction.LINK);
        assertThat(link.get().linkCode()).isEqualTo("ABC123");

        assertThat(channel().handleCallback(settings(), peer().callback("DANCE|1|2", "777"))).isEmpty();
    }

    @Test
    @DisplayName("RUL-05.02 API-RUL-30 link: 일회용 코드가 든 딥링크와 만료 시각")
    void linkReturnsDeepLink() {
        Instant expires = Instant.parse("2026-10-03T01:22:03Z");
        LinkResult r = channel().link(settings(), new LinkRequest(5, "ABC123", expires));
        assertThat(r.deepLink()).contains("ABC123");
        assertThat(r.expiresAt()).isEqualTo(expires);
    }
}
