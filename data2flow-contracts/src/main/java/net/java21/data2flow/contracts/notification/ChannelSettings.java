package net.java21.data2flow.contracts.notification;

import net.java21.data2flow.contracts.secret.Secret;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * 채널 한 개의 설정(notification_channels 한 행). 비밀값(봇 토큰·웹훅 시크릿)은 설정 JSON이 아니라 복호화된 {@link Secret}으로 온다.
 * {@code Secret}은 출력·직렬화가 언제나 가려지므로 이 객체를 로그에 남겨도 평문이 나가지 않는다.
 *
 * @param channelId      채널 ID
 * @param organizationId 조직 ID
 * @param config         채널 설정(채널의 {@link NotificationChannel#configSchema()}를 통과한 값)
 * @param secrets        비밀값 이름 → 값(예: {@code botToken}, {@code webhookSecret})
 */
public record ChannelSettings(long channelId, long organizationId, JsonNode config, Map<String, Secret> secrets) {

    public ChannelSettings {
        if (channelId < 1 || organizationId < 1 || config == null) {
            throw new IllegalArgumentException("channelId·organizationId·config는 필수입니다");
        }
        secrets = secrets == null ? Map.of() : Map.copyOf(secrets);
    }

    /** 비밀값. 없으면 null */
    public Secret secret(String name) {
        return secrets.get(name);
    }
}
