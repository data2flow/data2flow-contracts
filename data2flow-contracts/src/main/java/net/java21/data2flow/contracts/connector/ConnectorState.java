package net.java21.data2flow.contracts.connector;

/**
 * 소스 연결 상태(DSC domain-model §2.5 {@code source_runtimes.state}, §3.2 상태 전이).
 *
 * <p>{@code DISABLED ↔ CONNECTING → CONNECTED → DISCONNECTED → CONNECTING(백오프 1, 2, 4 … 최대 60초) → ERROR}.
 * 인증·TLS처럼 다시 시도해도 실패가 확실한 오류는 5회 뒤 ERROR를 유지하고 5분마다 다시 시도한다.
 */
public enum ConnectorState {
    CONNECTED, CONNECTING, DISCONNECTED, ERROR, DISABLED
}
