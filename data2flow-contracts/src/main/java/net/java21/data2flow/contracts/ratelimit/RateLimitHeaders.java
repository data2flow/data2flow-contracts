package net.java21.data2flow.contracts.ratelimit;

/**
 * 호출 한도 응답 헤더 이름(OPS-12.05, BR-OPS-22, api-rules §6). 서블릿 의존성이 없어 리액티브 gateway에서도 쓴다.
 */
public final class RateLimitHeaders {

    /** 창(window) 안에서 허용하는 요청 수 */
    public static final String LIMIT = "X-RateLimit-Limit";
    /** 창 안에서 남은 요청 수 */
    public static final String REMAINING = "X-RateLimit-Remaining";
    /** 한도가 다시 채워질 때까지 남은 초(지금부터의 초, 절대 시각 아님) */
    public static final String RESET = "X-RateLimit-Reset";
    /** 다시 시도해도 되는 때까지 남은 초 */
    public static final String RETRY_AFTER = "Retry-After";

    private RateLimitHeaders() {
    }
}
