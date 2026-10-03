package net.java21.data2flow.contracts.idempotency;

/**
 * 멱등 키의 범위(BR-OPS-20, 표준 테이블 {@code idempotency_keys}의 UNIQUE 열).
 *
 * @param organizationId 조직(신원 없는 내부 호출은 0)
 * @param userId         사용자(신원 없는 내부 호출은 0)
 * @param route          메서드 + 경로 템플릿(예: {@code POST /core/devices/{device-id}/commands}, 최대 200자)
 * @param key            {@code Idempotency-Key} 값(최대 64자)
 */
public record IdempotencyScope(long organizationId, long userId, String route, String key) {
}
