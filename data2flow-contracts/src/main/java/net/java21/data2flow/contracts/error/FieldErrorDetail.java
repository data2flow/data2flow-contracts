package net.java21.data2flow.contracts.error;

/** 입력 검증 실패 항목 {@code errors[{field, code, message}]} (design/api-rules.md §4.1) */
public record FieldErrorDetail(String field, String code, String message) {
}
