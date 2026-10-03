package net.java21.data2flow.contracts.error;

/**
 * 오류 코드. 이름은 UPPER_SNAKE이고 플랫폼 전체에서 하나의 뜻과 하나의 HTTP 상태만 가진다
 * (spec/detail/00-error-codes.md §1). 사용자 문구는 메시지 키 {@code error.<코드>}로 4개 언어에서 찾는다(ADR-037).
 */
public interface ErrorCode {

    /** 응답 {@code header.resultCode}에 그대로 나가는 값 */
    String code();

    /** HTTP 상태 코드. 422는 쓰지 않는다(ADR-035) */
    int httpStatus();

    default String messageKey() {
        return "error." + code();
    }
}
