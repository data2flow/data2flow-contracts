package net.java21.data2flow.contracts.connector;

import net.java21.data2flow.contracts.message.RawEnvelope;
import net.java21.data2flow.contracts.messaging.DedupKeys;

import java.time.Clock;
import java.util.function.Consumer;

/**
 * 커넥터 실행 환경(connectors.md §1.1). ingress가 세션마다 하나 만든다.
 *
 * @param instanceId     ingress 파드 이름({@code RawEnvelope.ingressInstance}, 상태 보고 {@code instanceId})
 * @param clock          수신 시각을 정하는 시계(테스트는 고정 시계)
 * @param statusListener 상태가 바뀔 때 부른다. ingress는 EVT-DSC-02로 보고한다
 */
public record ConnectorContext(String instanceId, Clock clock, Consumer<ConnectorStatus> statusListener) {

    public ConnectorContext {
        if (instanceId == null || instanceId.isBlank() || clock == null) {
            throw new IllegalArgumentException("instanceId·clock은 필수입니다");
        }
        statusListener = statusListener == null ? s -> { } : statusListener;
    }

    /** 받은 메시지를 원본 봉투로 싼다. 수신 시각은 지금, 중복 키는 {@link DedupKeys#detect} */
    public RawEnvelope envelope(SourceConfig source, String topic, byte[] payload) {
        return RawEnvelope.of(source.organizationId(), source.sourceId(), source.sourceType(), topic, payload,
                clock.instant(), instanceId, DedupKeys.detect(source.sourceId(), topic, payload));
    }

    public void reportStatus(ConnectorStatus status) {
        statusListener.accept(status);
    }
}
