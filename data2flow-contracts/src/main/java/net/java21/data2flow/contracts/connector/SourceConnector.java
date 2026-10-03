package net.java21.data2flow.contracts.connector;

import tools.jackson.databind.JsonNode;

/**
 * 커넥터 SPI(DSC-09.02, connectors.md §1.1). 외부 시스템(브로커·서버·장비) 하나와 연결해 받은 메시지를 원본 봉투로 넘긴다.
 *
 * <p>ingress의 커넥터 레지스트리가 구현을 모아 카탈로그로 보고하고(EVT-DSC-09), 소스마다 {@link #open}으로 세션 하나를 연다.
 * 이 네 가지(이 인터페이스, {@link ConnectorSession}, {@link RawSink}, {@link ConnectorDescriptor})만 구현하면 등록 화면,
 * 연결 테스트, 상태 표시가 자동으로 동작한다.
 *
 * <p><b>카탈로그 등록 조건(BR-DSC-23):</b> 커넥터 계약 테스트 키트({@code data2flow-contracts-test}의
 * {@code AbstractConnectorContractTest})와 라이선스 검사를 통과해야 한다. 키트는 특히 "기록이 끝나기 전에 상대에게 확인(ACK)하지
 * 않는다"를 확인한다(AT-DSC-18.1, DSC-09.03).
 */
public interface SourceConnector {

    /** 키, 이름, 버전, 지원 인증·형식, 확인 방식, 확장 방식, 송신 지원 */
    ConnectorDescriptor descriptor();

    /** 소스 등록 화면 폼을 만드는 설정 JSON Schema(2020-12, DSC-09.01) */
    JsonNode configSchema();

    /**
     * 연결 테스트(DSC-09.11, BR-DSC-07): DNS → TCP → TLS → 인증 → 구독 단계별 결과와 첫 메시지 미리보기(최대 10건).
     * 최대 15초, 결과는 저장하지 않는다. 테스트 client-id는 운영과 겹치지 않게 {@code {base}-test-{난수}}를 쓴다.
     * 연결에 실패해도 예외를 던지지 않고 실패한 단계를 결과에 담는다.
     */
    ConnectionTestResult test(SourceConfig config);

    /**
     * 소스 하나의 세션을 연다. 실제 연결은 {@link ConnectorSession#start()}에서 시작한다.
     *
     * @param sink 받은 메시지를 기록하는 곳. {@link RawSink#write}가 완료된 뒤에만 상대에게 확인을 보낸다
     * @param ctx  인스턴스 이름·시계·상태 보고 창구
     */
    ConnectorSession open(SourceConfig config, RawSink sink, ConnectorContext ctx);

    /** 카탈로그 보고(EVT-DSC-09) 항목 */
    default ConnectorCatalogEntry catalogEntry() {
        return ConnectorCatalogEntry.of(descriptor(), configSchema());
    }
}
