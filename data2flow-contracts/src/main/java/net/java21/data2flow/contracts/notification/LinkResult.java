package net.java21.data2flow.contracts.notification;

import java.time.Instant;

/**
 * 계정 연결 안내(API-RUL-30 응답 {@code {code, deepLink, expiresAt}}).
 *
 * @param deepLink  사용자가 열 링크(텔레그램 {@code https://t.me/{bot}?start={code}})
 * @param expiresAt 만료 시각
 */
public record LinkResult(String deepLink, Instant expiresAt) {

    public LinkResult {
        if (deepLink == null || deepLink.isBlank() || expiresAt == null) {
            throw new IllegalArgumentException("deepLink·expiresAt은 필수입니다");
        }
    }
}
