package net.java21.data2flow.contracts.connector;

import tools.jackson.databind.JsonNode;

import java.util.Set;

/**
 * 커넥터 카탈로그 보고 항목(EVT-DSC-09 {@code connectors[]}). core-api가 {@code connector_catalogs} 한 행으로 저장한다(BR-DSC-23).
 *
 * @param schema 설정 JSON Schema({@link SourceConnector#configSchema()})
 */
public record ConnectorCatalogEntry(String key, String name, String version, ConnectorCategory category, JsonNode schema,
                                    Set<AuthMethod> authMethods, Set<PayloadFormat> payloadFormats, AckMode ackMode,
                                    ScalingMode scaling, boolean supportsSend) {

    public ConnectorCatalogEntry {
        authMethods = authMethods == null ? Set.of() : Set.copyOf(authMethods);
        payloadFormats = payloadFormats == null ? Set.of() : Set.copyOf(payloadFormats);
    }

    public static ConnectorCatalogEntry of(ConnectorDescriptor d, JsonNode schema) {
        return new ConnectorCatalogEntry(d.key(), d.name(), d.version(), d.category(), schema, d.authMethods(),
                d.payloadFormats(), d.ackMode(), d.scaling(), d.supportsSend());
    }
}
