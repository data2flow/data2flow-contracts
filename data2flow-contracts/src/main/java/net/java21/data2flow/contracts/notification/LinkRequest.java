package net.java21.data2flow.contracts.notification;

import java.time.Instant;

/**
 * 계정 연결 시작(API-RUL-30 {@code messenger-links/start}). 공통 계층이 일회용 코드를 만들어 저장하고 채널은 딥링크만 만든다.
 *
 * @param userId    연결할 data2flow 사용자
 * @param code      일회용 코드
 * @param expiresAt 만료 시각(10분)
 */
public record LinkRequest(long userId, String code, Instant expiresAt) {

    public LinkRequest {
        if (userId < 1 || code == null || code.isBlank() || expiresAt == null) {
            throw new IllegalArgumentException("userId·code·expiresAt은 필수입니다");
        }
    }
}
