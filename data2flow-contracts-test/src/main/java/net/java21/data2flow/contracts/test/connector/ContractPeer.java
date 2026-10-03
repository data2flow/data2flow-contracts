package net.java21.data2flow.contracts.test.connector;

import java.util.List;

/**
 * 커넥터 계약 테스트의 "상대편"(외부 브로커·서버·장비). 커넥터 테스트가 Testcontainers로 띄운 실제 상대에 맞춰 구현한다.
 *
 * <p>예: MQTT는 테스트 클라이언트로 QoS 1 발행하고 브로커 쪽 확인 수(또는 세션에 남은 미확인 수)를 센다. Kafka는 소비자 그룹 커밋 오프셋,
 * 폴링 커넥터는 저장된 커서 위치, Webhook은 2xx 응답 수가 확인 수다.
 */
public interface ContractPeer {

    /** 상대편에서 메시지를 보낸다(브로커 발행, 서버 응답 준비, 장비 레지스터 값 변경 등). 순서대로 보낸다 */
    void publish(List<byte[]> payloads) throws Exception;

    /**
     * 상대편이 "받았다"고 확인받은 누적 메시지 수(PUBACK·ack·커밋 오프셋·저장 커서). 테스트는 시작 전 값을 기준으로 차이를 본다.
     * 확인 방식이 없는 커넥터(AckMode NONE·AUTO)는 -1을 돌려도 된다.
     */
    long acknowledgedCount();
}
