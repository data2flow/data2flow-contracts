package net.java21.data2flow.contracts.output;

/** 출력 연결 테스트·발송 실패 종류(API-DSC-32 {@code failureKind}). 테스트 실패는 HTTP 오류가 아니라 200 + {@code ok=false} */
public enum OutputFailureKind {
    AUTH, DNS, TLS, TIMEOUT, REFUSED, HTTP_STATUS
}
