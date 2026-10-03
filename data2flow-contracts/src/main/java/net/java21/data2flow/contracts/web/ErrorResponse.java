package net.java21.data2flow.contracts.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.java21.data2flow.contracts.error.FieldErrorDetail;

import java.util.List;

/** 실패 응답 {@code {header:{isSuccessful:false, …}, errors?}}. errors는 입력 검증 실패일 때만 붙는다. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(ApiHeader header, List<FieldErrorDetail> errors) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(ApiHeader.failure(code, message), List.of());
    }

    public static ErrorResponse of(String code, String message, List<FieldErrorDetail> errors) {
        return new ErrorResponse(ApiHeader.failure(code, message), errors == null ? List.of() : List.copyOf(errors));
    }
}
