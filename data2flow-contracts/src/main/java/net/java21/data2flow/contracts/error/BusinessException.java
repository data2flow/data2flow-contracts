package net.java21.data2flow.contracts.error;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 업무 규칙 위반을 알리는 예외. 공통 예외 처리기가 {@code {header:{isSuccessful:false, resultCode, resultMessage}}}로 바꾼다.
 * 문구 자리표시자 값은 {@code args}로 넘긴다(예: RATE_LIMITED의 남은 초).
 * 응답 헤더가 필요한 실패(429·409의 {@code Retry-After}, 429의 {@code X-RateLimit-*})는 {@link #withHeader(String, String)}로 싣는다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object[] args;
    private final transient List<FieldErrorDetail> errors;
    private final transient Map<String, String> headers = new LinkedHashMap<>();

    public BusinessException(ErrorCode errorCode, Object... args) {
        this(errorCode, List.of(), args);
    }

    public BusinessException(ErrorCode errorCode, List<FieldErrorDetail> errors, Object... args) {
        super(errorCode.code());
        this.errorCode = errorCode;
        this.args = args == null ? new Object[0] : args.clone();
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Object[] getArgs() {
        return args.clone();
    }

    public List<FieldErrorDetail> getErrors() {
        return errors;
    }

    /** 실패 응답에 붙일 헤더를 더한다(던지기 전에 호출) */
    public BusinessException withHeader(String name, String value) {
        headers.put(name, value);
        return this;
    }

    public Map<String, String> getHeaders() {
        return Map.copyOf(headers);
    }
}
