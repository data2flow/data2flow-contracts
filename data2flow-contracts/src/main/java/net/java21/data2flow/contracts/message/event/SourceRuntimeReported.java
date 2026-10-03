package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.java21.data2flow.contracts.connector.ConnectionErrorKind;
import net.java21.data2flow.contracts.connector.ConnectorState;

import java.time.Instant;

/**
 * EVT-DSC-02 {@code source.runtime.reported}: ingress 인스턴스 하나의 소스 연결 상태(30초마다, 상태가 바뀌면 즉시).
 * core-api가 {@code source_runtimes}에 저장한다.
 *
 * @param sourceId       데이터 소스 ID
 * @param instanceId     ingress 파드 이름
 * @param state          연결 상태
 * @param errorKind      실패 종류. 정상이면 null
 * @param errorMessage   실패 설명(500자 이하, 비밀값 없음). 정상이면 null
 * @param clientId       실제 사용한 client-id
 * @param connectedSince 연결된 시각. 연결 중이 아니면 null
 * @param reconnects24h  최근 24시간 재연결 횟수
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SourceRuntimeReported(long sourceId, String instanceId, ConnectorState state, ConnectionErrorKind errorKind,
                                    String errorMessage, String clientId, Instant connectedSince, int reconnects24h)
        implements EventPayload {
}
