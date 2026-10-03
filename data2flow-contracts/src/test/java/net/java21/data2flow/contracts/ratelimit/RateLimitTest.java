package net.java21.data2flow.contracts.ratelimit;

import net.java21.data2flow.contracts.error.BusinessException;
import net.java21.data2flow.contracts.error.CommonErrorCode;
import net.java21.data2flow.contracts.web.ErrorMessages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** OPS-12.05·BR-OPS-22: 429와 Retry-After, X-RateLimit-* (gateway·서비스 공통, 서블릿 의존 없음) */
class RateLimitTest {

    private final ErrorMessages messages = new ErrorMessages(null);

    @Test
    @DisplayName("[OPS-12.05][AT-OPS-25.6][TC-OPS-131] 한도 초과 → 429 RATE_LIMITED, Retry-After·X-RateLimit-*, '{n}초 후' 문구")
    void rejection() {
        // given
        RateLimitInfo info = new RateLimitInfo(60, 0, 30);
        // when
        RateLimitRejection rejection = RateLimitRejection.of(CommonErrorCode.RATE_LIMITED, info, messages, Locale.KOREAN);
        // then
        assertThat(rejection.status()).isEqualTo(429);
        assertThat(rejection.headers())
                .containsEntry("Retry-After", "30")
                .containsEntry("X-RateLimit-Limit", "60")
                .containsEntry("X-RateLimit-Remaining", "0")
                .containsEntry("X-RateLimit-Reset", "30");
        assertThat(rejection.body().header().isSuccessful()).isFalse();
        assertThat(rejection.body().header().resultCode()).isEqualTo("RATE_LIMITED");
        assertThat(rejection.body().header().resultMessage()).isEqualTo("요청이 너무 많습니다. 30초 후 다시 시도해 주세요");
        assertThat(RateLimitRejection.of(CommonErrorCode.AUTH_RATE_LIMITED, info, messages, Locale.ENGLISH).body().header().resultMessage())
                .isEqualTo("Too many requests. Please try again in 30 seconds");
    }

    @Test
    @DisplayName("[OPS-12.05] 정상 응답의 남은 한도 헤더, 남은 수는 0 아래로 내려가지 않고 Retry-After는 최소 1초")
    void headers() {
        RateLimitInfo info = new RateLimitInfo(600, -3, 0);
        assertThat(info.remaining()).isZero();
        assertThat(info.headers()).containsOnlyKeys("X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Reset");
        assertThat(info.exceededHeaders()).containsEntry("Retry-After", "1");
        assertThatThrownBy(() -> new RateLimitInfo(-1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[OPS-12.05] 서블릿 서비스는 429 예외에 헤더를 싣고, 429가 아닌 코드는 거부한다")
    void servletException() {
        BusinessException ex = new RateLimitInfo(5, 0, 12).exceeded(CommonErrorCode.AUTH_RATE_LIMITED);
        assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.AUTH_RATE_LIMITED);
        assertThat(ex.getArgs()).containsExactly(12L);
        assertThat(ex.getHeaders()).containsEntry("Retry-After", "12").containsEntry("X-RateLimit-Limit", "5");
        assertThatThrownBy(() -> new RateLimitInfo(5, 0, 12).exceeded(CommonErrorCode.INVALID_REQUEST))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
