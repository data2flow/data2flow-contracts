package net.java21.data2flow.contracts.identity;

import java.util.List;
import java.util.Locale;

/**
 * 서비스 사이에 오가는 헤더 이름(design/api-rules.md §6, design/auth.md §5).
 * X-USER-ID·X-ORG-ID·토큰 헤더는 gateway만 넣는다. gateway는 밖에서 들어온 같은 이름의 헤더를 먼저 지운다
 * ({@link #isGatewayOwned(String)}).
 */
public final class DataflowHeaders {

    public static final String USER_ID = "X-USER-ID";
    public static final String ORG_ID = "X-ORG-ID";
    /** 장기 토큰(MCP) 요청의 토큰 ID (api-rules §6) */
    public static final String ACCESS_TOKEN_ID = "X-ACCESS-TOKEN-ID";
    /** 장기 토큰 범위, 공백 또는 쉼표로 구분 (auth.md §5) */
    public static final String TOKEN_SCOPE = "X-TOKEN-SCOPE";
    public static final String REQUEST_ID = "X-REQUEST-ID";
    /** 내부 호출에서 호출한 서비스 이름(토큰 대신 표시만 한다, ADR-021) */
    public static final String CALLER_SERVICE = "X-CALLER-SERVICE";
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String RETRY_AFTER = "Retry-After";

    /** gateway가 밖에서 들어온 요청에서 지우는 헤더(정확한 이름) */
    public static final List<String> IDENTITY_HEADERS = List.of(USER_ID, ORG_ID, ACCESS_TOKEN_ID, TOKEN_SCOPE, CALLER_SERVICE);
    /** gateway가 밖에서 들어온 요청에서 지우는 헤더(접두사, auth.md §5 "X-TOKEN-*, X-INTERNAL-*") */
    public static final List<String> IDENTITY_HEADER_PREFIXES = List.of("X-TOKEN-", "X-INTERNAL-", "X-ACCESS-TOKEN-");

    private DataflowHeaders() {
    }

    /** gateway만 넣을 수 있는 헤더인가. 외부 요청에 있으면 지운다(BR-IAM-37) */
    public static boolean isGatewayOwned(String headerName) {
        if (headerName == null) {
            return false;
        }
        String upper = headerName.toUpperCase(Locale.ROOT);
        return IDENTITY_HEADERS.stream().anyMatch(h -> h.equals(upper))
                || IDENTITY_HEADER_PREFIXES.stream().anyMatch(upper::startsWith);
    }
}
