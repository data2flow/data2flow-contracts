package net.java21.data2flow.contracts.connector;

/**
 * 상대에게 "받았다"를 알리는 방식(DSC-09.03, connector_catalogs.ack_mode, connectors.md §1.2). 커넥터별로는:
 * MQTT QoS1·2·AMQP 0-9-1/1.0·Kafka(오프셋 수동 커밋)·NATS JetStream·Pub/Sub·Event Hubs(체크포인트)·Webhook(2xx) = AFTER_WRITE,
 * HTTP 폴링·Modbus·BACnet 폴링·OPC UA 읽기·파일 가져오기(처리한 파일·줄 위치) = CURSOR({@link PollCursorStore}),
 * OPC UA 구독·BACnet COV처럼 상대가 다시 보내 주지 않는 통지 = NONE, MQTT QoS 0·CoAP NON = NONE.
 */
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
