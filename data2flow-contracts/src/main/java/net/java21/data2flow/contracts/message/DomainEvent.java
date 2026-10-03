package net.java21.data2flow.contracts.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import net.java21.data2flow.contracts.message.event.EventPayload;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * 도메인 이벤트 봉투 v1. topic exchange {@code data2flow.events}로 가는 모든 메시지가 이 모양이다(architecture.md §4.5).
 *
 * <pre>{@code
 * {"v":1,"messageId":"…","type":"device.pending.created","organizationId":1,
 *  "occurredAt":"2026-10-03T02:40:09Z","requestId":"…","payload":{"deviceId":17,…}}
 * }</pre>
 *
 * <p>API 문서(EVT-*)의 "공통 헤더"(messageId·v·organizationId·X-REQUEST-ID·occurredAt)는 이 봉투 필드이고, AMQP 메시지 속성에도
 * 같은 값을 싣는다({@link net.java21.data2flow.contracts.messaging.MessageHeaders}). 각 이벤트 표의 "페이로드"는 {@code payload}다.
 * {@code type}은 발행 라우팅 키와 같다.
 *
 * @param v              스키마 버전(이벤트 종류별, 현재 모두 1)
 * @param messageId      메시지 ID. 소비자는 이것으로 중복을 거른다
 * @param type           이벤트 종류 = 라우팅 키({@link EventType#routingKey()})
 * @param organizationId 조직 ID
 * @param occurredAt     일이 일어난 시각(UTC)
 * @param requestId      원인 요청의 X-REQUEST-ID. 요청 밖(스케줄러 등)이면 null
 * @param payload        이벤트별 페이로드
 */
@MessageSchema(name = "domain-event", version = 1)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DomainEvent<P extends EventPayload>(
        int v,
        UUID messageId,
        String type,
        long organizationId,
        Instant occurredAt,
        String requestId,
        P payload) implements Message {

    public static final int VERSION = 1;

    public DomainEvent {
        Messages.requireVersion(v);
        Messages.require(messageId, "messageId");
        Messages.requireText(type, "type");
        Messages.requireId(organizationId, "organizationId");
        Messages.require(occurredAt, "occurredAt");
        Messages.require(payload, "payload");
    }

    /**
     * 새 이벤트를 만든다. 페이로드 타입이 종류와 맞지 않으면 {@link IllegalArgumentException}.
     *
     * @param requestId 원인 요청 ID. 없으면 null
     */
    public static <P extends EventPayload> DomainEvent<P> of(EventType type, long organizationId, P payload,
                                                             String requestId, Clock clock) {
        if (!type.payloadType().isInstance(payload)) {
            throw new IllegalArgumentException(type + " 페이로드는 " + type.payloadType().getSimpleName() + "여야 합니다");
        }
        return new DomainEvent<>(type.version(), UUID.randomUUID(), type.routingKey(), organizationId,
                clock.instant(), requestId, payload);
    }

    /** 이 코드가 아는 종류면 그 값. 발행 라우팅 키는 {@link #type()}을 그대로 쓴다 */
    @JsonIgnore
    public EventType eventType() {
        return EventType.fromRoutingKey(type)
                .orElseThrow(() -> new MessageFormatException("모르는 이벤트 종류입니다: " + type));
    }
}
