package net.java21.data2flow.contracts.sink;

import java.util.Set;

/**
 * Sink 연결 종류(sink_connections.type). 새 저장소는 action의 Sink 커넥터 플러그인으로 추가하므로(FLW-04.05) enum이 아니라 문자열이다.
 * Sink 커넥터 SPI와 계약 키트({@code SinkConnectorContractTest}, TC-FLW-087)는 SPI 패키지가 있는 data2flow-action에 있다.
 */
public final class SinkTypes {

    public static final String POSTGRESQL = "POSTGRESQL";
    public static final String MYSQL = "MYSQL";
    public static final String INFLUXDB = "INFLUXDB";

    /** 이 버전 계약이 아는 기본 종류 */
    public static final Set<String> BUILTIN = Set.of(POSTGRESQL, MYSQL, INFLUXDB);

    private SinkTypes() {
    }
}
