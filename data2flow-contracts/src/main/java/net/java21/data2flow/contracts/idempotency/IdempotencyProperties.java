package net.java21.data2flow.contracts.idempotency;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 멱등 처리 설정({@code data2flow.idempotency.*}).
 *
 * @param jdbcTable          설정하면 {@link JdbcIdempotencyStore}를 자동 구성한다(예: {@code data2flow_core.idempotency_keys})
 * @param ttl                키 보관 기간. BR-OPS-20은 24시간
 * @param inProgressTimeout  결과 없이 이 시간이 지난 키는 처리 중 파드가 죽은 것으로 보고 넘겨받는다
 * @param retryAfter         처리 중(409 IDEMPOTENCY_CONFLICT) 응답의 Retry-After
 * @param maxBodyBytes       키를 계산하려고 미리 읽는 본문 상한. 넘으면 413 PAYLOAD_TOO_LARGE
 */
@ConfigurationProperties(prefix = "data2flow.idempotency")
public record IdempotencyProperties(String jdbcTable, Duration ttl, Duration inProgressTimeout, Duration retryAfter,
                                    Integer maxBodyBytes) {

    public IdempotencyProperties {
        ttl = ttl == null ? Duration.ofHours(24) : ttl;
        inProgressTimeout = inProgressTimeout == null ? Duration.ofSeconds(60) : inProgressTimeout;
        retryAfter = retryAfter == null ? Duration.ofSeconds(2) : retryAfter;
        maxBodyBytes = maxBodyBytes == null ? 10 * 1024 * 1024 : maxBodyBytes;
    }

    public static IdempotencyProperties defaults() {
        return new IdempotencyProperties(null, null, null, null, null);
    }
}
