package net.java21.data2flow.contracts.connector;

/** 상대에게 "받았다"를 알리는 방식(DSC-09.03, connector_catalogs.ack_mode) */
public enum AckMode {
    /** 기록(confirm) 뒤 수동 확인: MQTT QoS1·2, AMQP, Kafka, JetStream, Pub/Sub, Webhook 응답. 무손실 */
    AFTER_WRITE,
    /** 프로토콜이 받자마자 자동 확인. 기록 전에 죽으면 유실될 수 있다 */
    AUTO,
    /** 폴링 커서(시각·ETag·페이지)를 기록 뒤 저장(BR-DSC-24). 무손실 */
    CURSOR,
    /** 확인 불가(MQTT QoS 0, CoAP NON). 화면에 "유실 가능" 배지 */
    NONE;

    /** 화면 "유실 가능" 표시 대상인가 */
    public boolean lossPossible() {
        return this == AUTO || this == NONE;
    }
}
