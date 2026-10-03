package net.java21.data2flow.contracts.error;

/** 공통 오류 코드 19개(spec/detail/00-error-codes.md §2). 도메인 코드는 각 서비스가 {@link ErrorCode}로 따로 정의한다. */
public enum CommonErrorCode implements ErrorCode {
    AUTH_INVALID_CREDENTIALS(401),
    AUTH_TOKEN_INVALID(401),
    AUTH_TOKEN_EXPIRED(401),
    AUTH_SESSION_REVOKED(401),
    AUTH_SESSION_EXPIRED(401),
    AUTH_PASSWORD_CHANGE_REQUIRED(403),
    AUTH_CSRF_INVALID(403),
    AUTH_RATE_LIMITED(429),
    AUTH_UNAVAILABLE(503),
    PERMISSION_DENIED(403),
    RESOURCE_NOT_FOUND(404),
    INVALID_REQUEST(400),
    VERSION_CONFLICT(409),
    IDEMPOTENCY_CONFLICT(409),
    IDEMPOTENCY_KEY_REUSED(409),
    RATE_LIMITED(429),
    PAYLOAD_TOO_LARGE(413),
    SERVICE_UNAVAILABLE(503),
    INTERNAL_ERROR(500);

    private final int httpStatus;

    CommonErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }
}
