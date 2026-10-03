package net.java21.data2flow.contracts.connector;

import net.java21.data2flow.contracts.message.RawEnvelope;

import java.util.concurrent.CompletionStage;

/**
 * 받은 원본의 기록 창구(DSC-09.03, connectors.md §1.2). ingress 구현은 {@code data2flow.raw} Super Stream에 발행하고
 * publisher confirm을 받으면 완료한다.
 *
 * <p><b>커넥터는 반환된 단계가 정상 완료된 뒤에만 상대에게 확인(MQTT PUBACK, AMQP ack, Kafka 오프셋 커밋, 폴링 커서 저장, Webhook 2xx)을
 * 보낸다.</b> 실패로 완료되면 확인하지 않는다(MQTT는 연결을 끊고 영속 세션으로 다시 접속해 재전송을 받는다, reliability-and-ha.md §2 ②).
 */
@FunctionalInterface
public interface RawSink {

    CompletionStage<Void> write(RawEnvelope envelope);
}
