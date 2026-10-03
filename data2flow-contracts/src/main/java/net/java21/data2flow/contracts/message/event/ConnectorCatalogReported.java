package net.java21.data2flow.contracts.message.event;

import net.java21.data2flow.contracts.connector.ConnectorCatalogEntry;

import java.util.List;

/**
 * EVT-DSC-09 {@code connector.catalog.reported}: ingress가 시작할 때 자기가 가진 커넥터 목록을 보고한다.
 * core-api가 {@code connector_catalogs}를 갱신한다(BR-DSC-23, DSC-09.02).
 *
 * @param instanceId ingress 파드 이름
 * @param connectors 커넥터 목록
 */
public record ConnectorCatalogReported(String instanceId, List<ConnectorCatalogEntry> connectors)
        implements EventPayload {

    public ConnectorCatalogReported {
        connectors = connectors == null ? List.of() : List.copyOf(connectors);
    }
}
