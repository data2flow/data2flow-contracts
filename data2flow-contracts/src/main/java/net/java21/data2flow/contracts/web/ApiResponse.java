package net.java21.data2flow.contracts.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 단건 응답 {@code {header, response}}. 결과가 없는 성공은 204로 응답하고 이 형식을 쓰지 않는다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(ApiHeader header, T response) {

    public static <T> ApiResponse<T> success(T response) {
        return new ApiResponse<>(ApiHeader.success(), response);
    }
}
