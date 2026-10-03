package net.java21.data2flow.contracts.connector;

import net.java21.data2flow.contracts.secret.Secret;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * 커넥터에 넘기는 소스 설정(API-DSC-50 {@code sources[]} 한 건). 비밀값은 설정 JSON이 아니라 {@link #secrets()}에 복호화된
 * {@link Secret}으로 온다. {@code Secret}은 출력·직렬화가 언제나 가려지므로 이 객체를 로그에 남겨도 평문이 나가지 않는다(BR-DSC-02).
 *
 * @param organizationId 조직 ID
 * @param sourceId       데이터 소스 ID
 * @param sourceType     {@link net.java21.data2flow.contracts.message.SourceTypes} 값
 * @param connectorKey   커넥터 키
 * @param config         커넥터별 설정 JSON(connectors.md §3)
 * @param secrets        비밀값 이름(예: {@code password}, {@code basic}) → 값
 * @param clientId       이 인스턴스가 쓸 client-id({@link net.java21.data2flow.contracts.messaging.ClientIds}). 해당 없으면 null
 */
public record SourceConfig(long organizationId, long sourceId, String sourceType, String connectorKey, JsonNode config,
                           Map<String, Secret> secrets, String clientId) {

    public SourceConfig {
        if (organizationId < 1 || sourceId < 1) {
            throw new IllegalArgumentException("organizationId·sourceId는 1 이상입니다");
        }
        if (sourceType == null || connectorKey == null || config == null) {
            throw new IllegalArgumentException("sourceType·connectorKey·config는 필수입니다");
        }
        secrets = secrets == null ? Map.of() : Map.copyOf(secrets);
    }

    /** 비밀값. 없으면 null */
    public Secret secret(String name) {
        return secrets.get(name);
    }
}
