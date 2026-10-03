package net.java21.data2flow.contracts.error;

import java.util.List;

/**
 * 업무 규칙 위반을 알리는 예외. 공통 예외 처리기가 {@code {header:{isSuccessful:false, resultCode, resultMessage}}}로 바꾼다.
 * 문구 자리표시자 값은 {@code args}로 넘긴다(예: RATE_LIMITED의 남은 초).
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object[] args;
    private final transient List<FieldErrorDetail> errors;

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
}
