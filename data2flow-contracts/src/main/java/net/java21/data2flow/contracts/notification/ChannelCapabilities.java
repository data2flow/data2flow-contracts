package net.java21.data2flow.contracts.notification;

import java.util.Set;

/**
 * 채널 기능 정보(OPS-06.06 {@code capabilities}, API-OPS-34).
 *
 * @param buttons                  인라인 버튼 지원. false면 공통 계층이 버튼 대신 바로가기 링크를 넣는다(BR-OPS-32)
 * @param formats                  지원 본문 서식
 * @param maxBodyLength            본문 최대 길이(텔레그램 4,096자, 템플릿은 4,000자 이하)
 * @param defaultRatePerMin        기본 분당 발송 한도(notification_channels.rate_limit_per_min 기본값, OPS-06.04)
 * @param callbackResponseLimitSec 콜백 응답 제한 시간(초). 콜백이 없으면 0
 */
public record ChannelCapabilities(boolean buttons, Set<BodyFormat> formats, int maxBodyLength, int defaultRatePerMin,
                                  int callbackResponseLimitSec) {

    public ChannelCapabilities {
        formats = formats == null || formats.isEmpty() ? Set.of(BodyFormat.PLAIN) : Set.copyOf(formats);
        if (maxBodyLength < 1 || defaultRatePerMin < 1 || callbackResponseLimitSec < 0) {
            throw new IllegalArgumentException("maxBodyLength·defaultRatePerMin은 1 이상, callbackResponseLimitSec는 0 이상입니다");
        }
    }

    /** 본문 서식 */
    public enum BodyFormat {
        PLAIN, MARKDOWN_V2, HTML
    }
}
