package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.java21.data2flow.contracts.connector.ConnectionErrorKind;
import net.java21.data2flow.contracts.connector.ConnectorState;

import java.time.Instant;

/**
 * EVT-DSC-04 {@code source.connection.changed}: 여러 ingress 보고를 모은 소스 대표 연결 상태가 바뀌었다(생산 core-api).
 *
 * @param sourceId  데이터 소스 ID
 * @param from      이전 대표 상태. 처음이면 null
 * @param to        새 대표 상태
 * @param errorKind 실패 종류. 정상이면 null
 * @param at        바뀐 시각
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SourceConnectionChanged(long sourceId, ConnectorState from, ConnectorState to,
                                      ConnectionErrorKind errorKind, Instant at) implements EventPayload {
}
