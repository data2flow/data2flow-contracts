package net.java21.data2flow.contracts.connector;

/** 연결 실패 종류(DSC domain-model §2.5 {@code source_runtimes.error_kind}). 화면이 원인별 안내 문구를 고른다 */
public enum ConnectionErrorKind {
    AUTH, DNS, TLS, TIMEOUT, REFUSED, PROTOCOL, QUOTA, OTHER
}
