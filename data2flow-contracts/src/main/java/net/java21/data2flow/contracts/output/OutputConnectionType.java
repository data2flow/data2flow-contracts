package net.java21.data2flow.contracts.output;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/** 출력 연결 종류(DSC-04.01, output_connections.type). 외부 DB 저장은 출력 연결이 아니라 플로우 Sink(FLW-04)다 */
public enum OutputConnectionType {
    /** 외부 MQTT 브로커로 발행(토픽 템플릿) */
    MQTT_PUBLISH,
    /** HTTP Webhook으로 POST(배치) */
    WEBHOOK,
    @JsonEnumDefaultValue
    UNKNOWN
}
