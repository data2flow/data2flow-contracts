package net.java21.data2flow.contracts.web;

import jakarta.validation.ConstraintViolationException;
import net.java21.data2flow.contracts.error.BusinessException;
import net.java21.data2flow.contracts.error.CommonErrorCode;
import net.java21.data2flow.contracts.error.ErrorCode;
import net.java21.data2flow.contracts.error.FieldErrorDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * 모든 실패를 공통 형식으로 바꾼다(design/api-rules.md §4). 화면은 resultCode로 분기하고 resultMessage는 표시만 한다.
 * 예상하지 못한 오류는 500 INTERNAL_ERROR로 답하고 상세는 로그에만 남긴다(X-REQUEST-ID로 찾는다).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ErrorMessages messages;

    public GlobalExceptionHandler(ErrorMessages messages) {
        this.messages = messages;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        ResponseEntity<ErrorResponse> response = respond(ex.getErrorCode(), ex.getErrors(), ex.getArgs());
        if (ex.getHeaders().isEmpty()) {
            return response;
        }
        HttpHeaders headers = new HttpHeaders();
        ex.getHeaders().forEach(headers::set);
        return ResponseEntity.status(response.getStatusCode()).headers(headers).body(response.getBody());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldErrorDetail(fe.getField(), fe.getCode(), fe.getDefaultMessage()))
                .toList();
        return respond(CommonErrorCode.INVALID_REQUEST, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex) {
        List<FieldErrorDetail> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldErrorDetail(v.getPropertyPath().toString(),
                        v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(), v.getMessage()))
                .toList();
        return respond(CommonErrorCode.INVALID_REQUEST, errors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingRequestHeaderException.class,
            HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        return respond(CommonErrorCode.INVALID_REQUEST, List.of());
    }

    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception ex) {
        return respond(CommonErrorCode.RESOURCE_NOT_FOUND, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception ex) {
        log.error("처리하지 못한 예외", ex);
        return respond(CommonErrorCode.INTERNAL_ERROR, List.of(), RequestIdFilter.currentRequestId());
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode code, List<FieldErrorDetail> errors, Object... args) {
        return ResponseEntity.status(code.httpStatus())
                .body(ErrorResponse.of(code.code(), messages.resolve(code, args), errors));
    }
}
