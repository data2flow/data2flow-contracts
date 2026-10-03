package net.java21.data2flow.contracts.ratelimit;

import net.java21.data2flow.contracts.error.ErrorCode;
import net.java21.data2flow.contracts.web.ErrorMessages;
import net.java21.data2flow.contracts.web.ErrorResponse;

import java.util.Locale;
import java.util.Map;

/**
 * 429 응답 한 벌(상태, 헤더, 본문). 서블릿에 의존하지 않으므로 리액티브 gateway 필터가 그대로 써서 응답한다
 * (TC-OPS-131: 429, resultCode RATE_LIMITED, "{n}초 후 다시 시도해 주세요").
 *
 * <pre>{@code
 * RateLimitRejection r = RateLimitRejection.of(CommonErrorCode.RATE_LIMITED, info, errorMessages, locale);
 * response.setStatusCode(HttpStatusCode.valueOf(r.status()));
 * r.headers().forEach(response.getHeaders()::set);
 * // 본문은 r.body()를 JSON으로
 * }</pre>
 */
public record RateLimitRejection(int status, Map<String, String> headers, ErrorResponse body) {

    public RateLimitRejection {
        headers = Map.copyOf(headers);
    }

    public static RateLimitRejection of(ErrorCode code, RateLimitInfo info, ErrorMessages messages, Locale locale) {
        RateLimitInfo.requireTooManyRequests(code);
        String message = messages.resolve(code, locale, info.retryAfterSeconds());
        return new RateLimitRejection(code.httpStatus(), info.exceededHeaders(), ErrorResponse.of(code.code(), message));
    }
}
