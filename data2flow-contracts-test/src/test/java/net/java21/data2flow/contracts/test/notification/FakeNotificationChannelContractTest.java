package net.java21.data2flow.contracts.test.notification;

import net.java21.data2flow.contracts.notification.ChannelSettings;
import net.java21.data2flow.contracts.notification.NotificationChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** OPS-06.06 TC-OPS-141·142: 가짜 채널(버튼 없음·있음)이 채널 계약 키트를 통과한다(서비스 채널 테스트가 상속하는 방법의 예) */
class FakeNotificationChannelContractTest {

    @Nested
    class WithoutButtons extends AbstractNotificationChannelContractTest {
        private final FakeNotificationChannel fake = new FakeNotificationChannel();

        @Override
        protected NotificationChannel channel() {
            return fake;
        }

        @Override
        protected ChannelSettings settings() {
            return fake.settings(2, 1);
        }

        @Override
        protected ChannelTestPeer peer() {
            return fake;
        }

        @Override
        protected String recipientAddress() {
            return "ops-room";
        }

        @Test
        @DisplayName("OPS-06.06 TC-OPS-141 버튼 없는 채널로 보낸 본문에는 [확인] 버튼 대신 바로가기 링크가 있다")
        void sentBodyHasLinkInsteadOfButtons() {
            fake.nextReply(ChannelTestPeer.Reply.OK);
            fake.send(settings(), message().adaptTo(fake.capabilities()));
            assertThat(fake.sent()).last().satisfies(m -> {
                assertThat(m.buttons()).isEmpty();
                assertThat(m.body()).contains("/alarms/9001");
            });
            assertThat(fake.key()).isEqualTo(FakeNotificationChannel.KEY);
            assertThat(fake.available()).isTrue();
        }
    }

    @Nested
    class WithButtons extends AbstractNotificationChannelContractTest {
        private final FakeNotificationChannel fake = new FakeNotificationChannel(true, "s3cr3t");

        @Override
        protected NotificationChannel channel() {
            return fake;
        }

        @Override
        protected ChannelSettings settings() {
            return fake.settings(3, 1);
        }

        @Override
        protected ChannelTestPeer peer() {
            return fake;
        }

        @Override
        protected String recipientAddress() {
            return "-100123";
        }

        @Test
        @DisplayName("RUL-05.02 비밀값이 없는 설정이나 헤더가 없는 요청은 검증을 통과하지 못하고, 보낸 사람이 없는 콜백은 무시한다")
        void verifyEdgeCases() {
            ChannelSettings noSecret = new ChannelSettings(3, 1, settings().config(), null);
            assertThat(fake.verify(noSecret, fake.callback("ACK|1|d", "7"))).isFalse();
            assertThat(fake.verify(settings(), new net.java21.data2flow.contracts.notification.InboundRequest(null,
                    "{}".getBytes()))).isFalse();
            assertThat(fake.handleCallback(settings(), new net.java21.data2flow.contracts.notification.InboundRequest(null,
                    "{\"data\":\"ACK|1|d\"}".getBytes()))).isEmpty();
            assertThat(fake.handleCallback(settings(), fake.callback("ACK||", "7")))
                    .hasValueSatisfying(c -> assertThat(c.alarmId()).isNull());
        }
    }
}
