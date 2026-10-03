package net.java21.data2flow.contracts.ratelimit;

import net.java21.data2flow.contracts.error.BusinessException;
import net.java21.data2flow.contracts.error.ErrorCode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 한 호출 시점의 한도 상태(OPS-12.05, BR-OPS-22). gateway의 Redis 토큰 버킷 결과, 서비스 내부 한도(예: 로그인 시도) 결과를
 * 같은 헤더로 내보낼 때 쓴다.
 *
 * @param limit        창 안 허용 수
 * @param remaining    남은 수(0 이상)
 * @param resetSeconds 한도가 다시 채워질 때까지 남은 초
 */
public record RateLimitInfo(long limit, long remaining, long resetSeconds) {

    public RateLimitInfo {
        if (limit < 0 || resetSeconds < 0) {
            throw new IllegalArgumentException("limit·resetSeconds는 0 이상이어야 합니다");
        }
        remaining = Math.max(0, remaining);
    }

    /** 정상 응답에 붙이는 남은 한도 헤더 */
    public Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(RateLimitHeaders.LIMIT, Long.toString(limit));
        headers.put(RateLimitHeaders.REMAINING, Long.toString(remaining));
        headers.put(RateLimitHeaders.RESET, Long.toString(resetSeconds));
        return headers;
    }

    /** 한도를 넘었을 때 다시 시도할 수 있는 초. 0초로 알려 즉시 재시도 폭주가 나지 않게 최소 1초 */
    public long retryAfterSeconds() {
        return Math.max(1, resetSeconds);
    }

    /** 429 응답 헤더: 남은 한도 헤더 + Retry-After */
    public Map<String, String> exceededHeaders() {
        Map<String, String> headers = headers();
        headers.put(RateLimitHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds()));
        return headers;
    }

    /**
     * 서블릿 서비스용: 429 예외. 공통 예외 처리기가 헤더와 "{n}초 후" 문구를 넣는다.
     *
     * @param code {@code RATE_LIMITED}, 인증 경로는 {@code AUTH_RATE_LIMITED}, 또는 429 도메인 코드
     */
    public BusinessException exceeded(ErrorCode code) {
        requireTooManyRequests(code);
        BusinessException ex = new BusinessException(code, retryAfterSeconds());
        exceededHeaders().forEach(ex::withHeader);
        return ex;
    }

    static void requireTooManyRequests(ErrorCode code) {
        if (code.httpStatus() != 429) {
            throw new IllegalArgumentException("429 코드가 아닙니다: " + code.code());
        }
    }
}
