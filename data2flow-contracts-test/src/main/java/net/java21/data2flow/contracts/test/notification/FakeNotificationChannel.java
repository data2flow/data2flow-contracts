package net.java21.data2flow.contracts.test.notification;

import net.java21.data2flow.contracts.message.MessageCodec;
import net.java21.data2flow.contracts.notification.CallbackAction;
import net.java21.data2flow.contracts.notification.CallbackCommand;
import net.java21.data2flow.contracts.notification.ChannelCapabilities;
import net.java21.data2flow.contracts.notification.ChannelMessage;
import net.java21.data2flow.contracts.notification.ChannelSettings;
import net.java21.data2flow.contracts.notification.InboundRequest;
import net.java21.data2flow.contracts.notification.LinkRequest;
import net.java21.data2flow.contracts.notification.LinkResult;
import net.java21.data2flow.contracts.notification.NotificationChannel;
import net.java21.data2flow.contracts.notification.SendResult;
import net.java21.data2flow.contracts.secret.Secret;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 테스트용 가짜 채널({@code FAKE}, TC-OPS-141). 공통 계층(action {@code notification})이 채널 키로 분기하지 않는지 확인할 때 SPI로
 * 등록한다. 버튼 지원 여부를 정할 수 있고(기본 {@code buttons=false}), 보낸 메시지를 메모리에 남긴다. {@link ChannelTestPeer}도 겸한다.
 *
 * <p>콜백 형식: 헤더 {@code X-Fake-Secret}(설정 비밀값 {@code secret}), 본문 {@code {"from":"777","data":"ACK|9001|d-1"}} 또는
 * {@code {"from":"777","text":"/start ABC123"}}.
 */
public class FakeNotificationChannel implements NotificationChannel, ChannelTestPeer {

    public static final String KEY = "FAKE";
    public static final String SECRET_HEADER = "X-Fake-Secret";
    public static final String SECRET_NAME = "secret";

    private static final MessageCodec CODEC = MessageCodec.create();
    private final boolean buttons;
    private final Deque<Reply> replies = new ArrayDeque<>();
    private final List<ChannelMessage> sent = new CopyOnWriteArrayList<>();
    private final AtomicInteger delivered = new AtomicInteger();
    private final AtomicInteger sequence = new AtomicInteger();
    private final String secret;

    public FakeNotificationChannel(boolean buttons, String secret) {
        this.buttons = buttons;
        this.secret = secret;
    }

    /** 버튼 없는 가짜 채널(TC-OPS-141 기본) */
    public FakeNotificationChannel() {
        this(false, "fake-secret");
    }

    /** 이 채널에 맞는 설정(비밀값 포함) */
    public ChannelSettings settings(long channelId, long organizationId) {
        return new ChannelSettings(channelId, organizationId, CODEC.mapper().createObjectNode().put("room", "ops"),
                java.util.Map.of(SECRET_NAME, Secret.of(secret)));
    }

    /** 보낸(성공한) 메시지 */
    public List<ChannelMessage> sent() {
        return List.copyOf(sent);
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public JsonNode configSchema() {
        return CODEC.mapper().readTree("""
                {"type":"object","required":["room"],"additionalProperties":false,
                 "properties":{"room":{"type":"string","minLength":1,"title":"대화방"}}}""");
    }

    @Override
    public ChannelCapabilities capabilities() {
        return new ChannelCapabilities(buttons, Set.of(ChannelCapabilities.BodyFormat.PLAIN), 1000, 60, buttons ? 10 : 0);
    }

    @Override
    public synchronized SendResult send(ChannelSettings settings, ChannelMessage message) {
        Reply reply = replies.isEmpty() ? Reply.OK : replies.poll();
        return switch (reply) {
            case OK -> {
                sent.add(message);
                delivered.incrementAndGet();
                yield SendResult.success("fake-" + sequence.incrementAndGet());
            }
            case TRANSIENT -> SendResult.transientFailure("503 Service Unavailable", Duration.ofSeconds(1));
            case PERMANENT -> SendResult.permanentFailure("403 Forbidden");
        };
    }

    @Override
    public boolean verify(ChannelSettings settings, InboundRequest request) {
        Secret expected = settings.secret(SECRET_NAME);
        String given = request.header(SECRET_HEADER);
        if (expected == null || given == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.reveal().getBytes(StandardCharsets.UTF_8),
                given.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Optional<CallbackCommand> handleCallback(ChannelSettings settings, InboundRequest request) {
        JsonNode body = CODEC.mapper().readTree(request.bodyAsString());
        String from = body.path("from").asString("");
        if (from.isBlank()) {
            return Optional.empty();
        }
        String text = body.path("text").asString("");
        if (text.startsWith("/start ")) {
            return Optional.of(CallbackCommand.link(from, text.substring("/start ".length()).trim()));
        }
        String data = body.path("data").asString("");
        Optional<CallbackAction> action = CallbackAction.fromCallbackData(data);
        if (action.isEmpty()) {
            return Optional.empty();
        }
        String[] parts = data.split("\\|", -1);
        Long alarmId = parts.length > 1 && !parts[1].isEmpty() ? Long.parseLong(parts[1]) : null;
        String deliveryId = parts.length > 2 && !parts[2].isEmpty() ? parts[2] : null;
        return Optional.of(CallbackCommand.button(action.get(), from, alarmId, deliveryId, null, null));
    }

    @Override
    public LinkResult link(ChannelSettings settings, LinkRequest request) {
        return new LinkResult("https://fake.example/link?start=" + request.code(), request.expiresAt());
    }

    // ChannelTestPeer

    @Override
    public synchronized void nextReply(Reply reply) {
        replies.add(reply);
    }

    @Override
    public int delivered() {
        return delivered.get();
    }

    @Override
    public InboundRequest callback(String callbackData, String externalUserId) {
        return inbound(secret, "{\"from\":\"" + externalUserId + "\",\"data\":\"" + callbackData + "\"}");
    }

    @Override
    public InboundRequest callbackWithWrongSecret(String callbackData, String externalUserId) {
        return inbound(secret + "-wrong", "{\"from\":\"" + externalUserId + "\",\"data\":\"" + callbackData + "\"}");
    }

    @Override
    public InboundRequest linkStart(String code, String externalUserId) {
        return inbound(secret, "{\"from\":\"" + externalUserId + "\",\"text\":\"/start " + code + "\"}");
    }

    private static InboundRequest inbound(String secretValue, String body) {
        return new InboundRequest(java.util.Map.of(SECRET_HEADER, secretValue), body.getBytes(StandardCharsets.UTF_8));
    }
}
