package net.java21.data2flow.contracts.connector;

import net.java21.data2flow.contracts.message.event.SourceRuntimeReported;

import java.time.Instant;

/**
 * 세션 상태와 지표(connectors.md §1.1 {@code ConnectorStatus}, EVT-DSC-02 원천).
 *
 * @param state          연결 상태
 * @param errorKind      실패 종류. 정상이면 null
 * @param errorMessage   실패 설명(비밀값 없이 500자 이하). 정상이면 null
 * @param clientId       실제 사용한 client-id. 해당 없으면 null
 * @param connectedSince 연결된 시각. 연결 중이 아니면 null
 * @param reconnects24h  최근 24시간 재연결 횟수
 * @param received       세션이 연 뒤 {@link RawSink}로 넘긴 메시지 수
 * @param lastReceivedAt 마지막으로 넘긴 시각. 없으면 null
 */
public record ConnectorStatus(ConnectorState state, ConnectionErrorKind errorKind, String errorMessage, String clientId,
                              Instant connectedSince, int reconnects24h, long received, Instant lastReceivedAt) {

    public static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    public ConnectorStatus {
        if (state == null) {
            throw new IllegalArgumentException("state는 필수입니다");
        }
        if (state == ConnectorState.ERROR && errorKind == null) {
            throw new IllegalArgumentException("ERROR 상태에는 errorKind가 필요합니다");
        }
        if (errorMessage != null && errorMessage.length() > MAX_ERROR_MESSAGE_LENGTH) {
            errorMessage = errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
        }
    }

    public static ConnectorStatus of(ConnectorState state) {
        return new ConnectorStatus(state, null, null, null, null, 0, 0, null);
    }

    public static ConnectorStatus error(ConnectionErrorKind kind, String message) {
        return new ConnectorStatus(ConnectorState.ERROR, kind, message, null, null, 0, 0, null);
    }

    /** EVT-DSC-02 페이로드로 바꾼다 */
    public SourceRuntimeReported toReport(long sourceId, String instanceId) {
        return new SourceRuntimeReported(sourceId, instanceId, state, errorKind, errorMessage, clientId,
                connectedSince, reconnects24h);
    }
}
