package net.java21.data2flow.contracts.identity;

/**
 * 서비스 사이에 오가는 헤더 이름(design/api-rules.md §6, design/auth.md).
 * X-USER-ID·X-ORG-ID는 gateway만 넣는다. gateway는 밖에서 들어온 같은 이름의 헤더를 먼저 지운다.
 */
public final class DataflowHeaders {

    public static final String USER_ID = "X-USER-ID";
    public static final String ORG_ID = "X-ORG-ID";
    public static final String REQUEST_ID = "X-REQUEST-ID";
    /** 내부 호출에서 호출한 서비스 이름(토큰 대신 표시만 한다, ADR-021) */
    public static final String CALLER_SERVICE = "X-CALLER-SERVICE";
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    /** gateway가 밖에서 들어온 요청에서 지우는 헤더 */
    public static final java.util.List<String> IDENTITY_HEADERS = java.util.List.of(USER_ID, ORG_ID, CALLER_SERVICE);

    private DataflowHeaders() {
    }
}
