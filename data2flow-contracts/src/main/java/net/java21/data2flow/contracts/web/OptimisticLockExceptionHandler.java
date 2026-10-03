package net.java21.data2flow.contracts.web;

import net.java21.data2flow.contracts.error.CommonErrorCode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * JPA {@code @Version}·Spring Data JDBC의 낙관적 잠금 충돌을 409 {@code VERSION_CONFLICT}로 바꾼다(OPS-12.04, BR-OPS-21).
 * spring-tx가 있는 서비스에서만 켜진다. 공통 예외 처리기보다 먼저 본다.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class OptimisticLockExceptionHandler {

    private final ErrorMessages messages;

    public OptimisticLockExceptionHandler(ErrorMessages messages) {
        this.messages = messages;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handle(OptimisticLockingFailureException ex) {
        CommonErrorCode code = CommonErrorCode.VERSION_CONFLICT;
        return ResponseEntity.status(code.httpStatus()).body(ErrorResponse.of(code.code(), messages.resolve(code)));
    }
}
