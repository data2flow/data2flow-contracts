package net.java21.data2flow.contracts.notification;

import net.java21.data2flow.contracts.alarm.AlarmSeverity;
import net.java21.data2flow.contracts.secret.Secret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** OPS-06.06 BR-OPS-32: 알림 채널 SPI 공통 타입(채널과 무관한 메시지, 결과 분류, 콜백 명령) */
class NotificationChannelTypesTest {

    private static final ChannelCapabilities TELEGRAM = new ChannelCapabilities(true,
            Set.of(ChannelCapabilities.BodyFormat.MARKDOWN_V2), 4096, 20, 10);
    private static final ChannelCapabilities NO_BUTTONS = new ChannelCapabilities(false, null, 40, 60, 0);

    private static ChannelMessage message(String body) {
        return new ChannelMessage("d-key", "-100123", "실습실 고온", body, AlarmSeverity.MAJOR,
                "https://data2flow.java21.net/alarms/9001",
                List.of(new ChannelButton("확인", CallbackAction.ACK, 9001L, "d-1"),
                        new ChannelButton("30분 무음", CallbackAction.MUTE_30M, 9001L, "d-1")), "ko", null);
    }

    @Test
    @DisplayName("OPS-06.06 TC-OPS-141 BR-OPS-32 버튼을 못 쓰는 채널(buttons=false)은 버튼 대신 바로가기 링크가 들어가고 길이를 지킨다")
    void adaptToCapabilities() {
        ChannelMessage original = message("온도 29.4℃");
        ChannelMessage telegram = original.adaptTo(TELEGRAM);
        assertThat(telegram.buttons()).hasSize(2);
        assertThat(telegram.body()).isEqualTo("온도 29.4℃");

        ChannelMessage plain = original.adaptTo(NO_BUTTONS);
        assertThat(plain.buttons()).isEmpty();
        assertThat(plain.body()).endsWith("\nhttps://data2flow.java21.net/alarms/9001");
        assertThat(plain.body().length()).isLessThanOrEqualTo(80);

        ChannelMessage longBody = message("가".repeat(100)).adaptTo(new ChannelCapabilities(false, null, 60, 60, 0));
        assertThat(longBody.body()).hasSize(60).contains(ChannelMessage.ELLIPSIS).endsWith("alarms/9001");
        ChannelMessage alreadyLinked = message("보기 https://data2flow.java21.net/alarms/9001").adaptTo(
                new ChannelCapabilities(false, null, 200, 60, 0));
        assertThat(alreadyLinked.body()).isEqualTo("보기 https://data2flow.java21.net/alarms/9001");
        ChannelMessage truncatedWithButtons = message("나".repeat(50)).adaptTo(new ChannelCapabilities(true, null, 10, 20, 0));
        assertThat(truncatedWithButtons.body()).hasSize(10).endsWith(ChannelMessage.ELLIPSIS);
        assertThat(new ChannelMessage("k", "a", null, null, null, null, null, "en", "m-1").body()).isEmpty();
        assertThatThrownBy(() -> new ChannelMessage(" ", "a", null, "b", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"200,SUCCESS", "204,SUCCESS", "408,TRANSIENT_FAILURE", "429,TRANSIENT_FAILURE", "500,TRANSIENT_FAILURE",
            "503,TRANSIENT_FAILURE", "400,PERMANENT_FAILURE", "401,PERMANENT_FAILURE", "403,PERMANENT_FAILURE",
            "404,PERMANENT_FAILURE", "302,PERMANENT_FAILURE"})
    @DisplayName("OPS-06.03 TC-OPS-063 TC-OPS-142 send 결과 분류: 2xx 성공, 408·429·5xx 일시 실패(재시도), 그 밖 영구 실패")
    void classifyHttp(int status, SendResult.Outcome expected) {
        assertThat(SendResult.classifyHttpStatus(status)).isEqualTo(expected);
    }

    @Test
    @DisplayName("RUL-03.05 BR-RUL-17 일시 실패만 재시도 대상이고, 실패에는 원인이 필요하며 500자로 자른다")
    void sendResults() {
        assertThat(SendResult.success("m-1").retryable()).isFalse();
        SendResult t = SendResult.transientFailure("429 Too Many Requests", Duration.ofSeconds(3));
        assertThat(t.retryable()).isTrue();
        assertThat(t.retryAfter()).hasSeconds(3);
        assertThat(SendResult.permanentFailure("x".repeat(600)).error()).hasSize(SendResult.MAX_ERROR_LENGTH);
        assertThatThrownBy(() -> SendResult.permanentFailure(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SendResult(null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("RUL-05.02 TC-RUL-093 BR-RUL-18 버튼 콜백 데이터 ↔ 공통 명령(ACK·MUTE_30M·APPROVE·REJECT), 계정 연결은 LINK + 코드")
    void callbacks() {
        ChannelButton ack = new ChannelButton("확인", CallbackAction.ACK, 9001L, "d-1");
        assertThat(ack.callbackData()).isEqualTo("ACK|9001|d-1");
        assertThat(new ChannelButton("승인", CallbackAction.APPROVE, null, null).callbackData()).isEqualTo("APPROVE||");
        assertThat(CallbackAction.fromCallbackData("MUTE_30M|9001|d-1")).contains(CallbackAction.MUTE_30M);
        assertThat(CallbackAction.fromCallbackData("UNKNOWN|1|2")).isEmpty();
        assertThat(CallbackAction.fromCallbackData("DANCE")).isEmpty();
        assertThat(CallbackAction.fromCallbackData(null)).isEmpty();
        assertThatThrownBy(() -> new ChannelButton(" ", CallbackAction.ACK, null, null)).isInstanceOf(IllegalArgumentException.class);

        CallbackCommand cmd = CallbackCommand.button(CallbackAction.ACK, "777", 9001L, "d-1", "42", "cb-1");
        assertThat(cmd.alarmId()).isEqualTo(9001L);
        assertThat(CallbackCommand.link("777", "ABC123").linkCode()).isEqualTo("ABC123");
        assertThatThrownBy(() -> new CallbackCommand(CallbackAction.LINK, "777", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CallbackCommand.button(CallbackAction.ACK, " ", 1L, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(net.java21.data2flow.contracts.message.MessageCodec.create().mapper().readValue("\"SNOOZE\"", CallbackAction.class)).isEqualTo(CallbackAction.UNKNOWN);
    }

    @Test
    @DisplayName("API-RUL-31 콜백 원본: 헤더 이름은 대소문자 무시, 본문은 복사본")
    void inboundRequest() {
        byte[] body = "{\"update_id\":1}".getBytes();
        InboundRequest req = new InboundRequest(Map.of("X-Telegram-Bot-Api-Secret-Token", "s3cr3t"), body);
        body[0] = 'X';
        assertThat(req.header("x-telegram-bot-api-secret-token")).isEqualTo("s3cr3t");
        assertThat(req.header("X-TELEGRAM-BOT-API-SECRET-TOKEN")).isEqualTo("s3cr3t");
        assertThat(req.bodyAsString()).startsWith("{");
        req.body()[0] = 'Y';
        assertThat(req.bodyAsString()).startsWith("{");
        assertThat(new InboundRequest(null, null).body()).isEmpty();
        assertThat(new InboundRequest(null, null).header("a")).isNull();
    }

    @Test
    @DisplayName("OPS-06.01 채널 설정·기능·계정 연결 값 검증, 비밀값은 가려진 채로 다닌다")
    void settingsAndLink() {
        ChannelSettings s = new ChannelSettings(2, 1, JsonMapper.builder().build().createObjectNode(),
                Map.of("botToken", Secret.of("123:abc")));
        assertThat(s.secret("botToken").reveal()).isEqualTo("123:abc");
        assertThat(s.toString()).doesNotContain("123:abc");
        assertThat(s.secret("none")).isNull();
        assertThat(new ChannelSettings(2, 1, JsonMapper.builder().build().createObjectNode(), null).secrets()).isEmpty();
        assertThatThrownBy(() -> new ChannelSettings(0, 1, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(NO_BUTTONS.formats()).containsExactly(ChannelCapabilities.BodyFormat.PLAIN);
        assertThatThrownBy(() -> new ChannelCapabilities(true, null, 0, 1, 0)).isInstanceOf(IllegalArgumentException.class);
        Instant exp = Instant.parse("2026-10-03T01:22:03Z");
        assertThat(new LinkRequest(5, "ABC123", exp).code()).isEqualTo("ABC123");
        assertThat(new LinkResult("https://t.me/d2f_bot?start=ABC123", exp).deepLink()).contains("ABC123");
        assertThatThrownBy(() -> new LinkRequest(0, "c", exp)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LinkResult(" ", exp)).isInstanceOf(IllegalArgumentException.class);
        assertThat(DeliveryStatus.SENT.terminal()).isTrue();
        assertThat(DeliveryStatus.RETRYING.terminal()).isFalse();
    }
}
