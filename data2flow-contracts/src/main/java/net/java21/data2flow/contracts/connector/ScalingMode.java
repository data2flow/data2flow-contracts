package net.java21.data2flow.contracts.connector;

/** 커넥터 확장 방식(DSC-09.10, connectors.md §1.3) */
public enum ScalingMode {
    /** 모든 ingress 인스턴스가 나눠 받는다(MQTT5 공유 구독, Kafka 소비자 그룹, AMQP 경쟁 소비, JetStream, Pub/Sub, Webhook) */
    SCALABLE,
    /** 두 인스턴스가 동시에 같은 것을 받고 pipeline이 중복을 거른다(MQTT 3.1.1, ADR-015) */
    DUAL_ACTIVE,
    /** 리스를 가진 리더 1대만 실행한다(폴링, OPC UA 세션, CoAP observe, BR-DSC-26) */
    SINGLETON
}
