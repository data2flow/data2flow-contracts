package net.java21.data2flow.contracts.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import net.java21.data2flow.contracts.command.ActionIdempotencyKeys;
import net.java21.data2flow.contracts.command.ActionKind;
import net.java21.data2flow.contracts.command.CommandPayload;
import net.java21.data2flow.contracts.command.CommandPriority;
import net.java21.data2flow.contracts.command.CommandSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * 행동 요청 v1(ACT-api §5.1, EVT-FLW-05): direct exchange {@code data2flow.actions} → Quorum 큐 {@code action.commands}·
 * {@code action.notifications}·{@code action.sinks}. flow-engine·core-api가 아웃박스에 기록하고 릴레이가 발행한다.
 *
 * <pre>{@code
 * {"v":1,"messageId":"…","idempotencyKey":"b3c1…(sha256)","kind":"COMMAND","organizationId":1,
 *  "createdAt":"2026-10-03T01:12:03.450Z",
 *  "source":{"type":"FLOW","flowId":"f-7f3a","flowVersion":13,"nodeId":"n-act-1","triggerMessageId":"7f3a…"},
 *  "priority":"AUTO","validUntil":"2026-10-03T01:22:03Z",
 *  "payload":{"target":{"spaceId":31,"relation":"controls","capability":"Thermostat","includeChildren":false},
 *             "capability":"Thermostat","command":"set","args":{"mode":"cool","targetTemperature":24},"awaitResult":true}}
 * }</pre>
 *
 * <p>소비 규칙(BR-ACT-02): {@code executed_actions}에 {@code idempotencyKey}가 있으면 실행하지 않고 이전 결과로 EVT-ACT-01을 다시 낸다.
 * 처리 후 ACK, 실패 시 NACK(재시도) → delivery-limit 5 초과 시 DLQ. 형식 오류·모르는 버전은 재시도 없이 DLQ.
 *
 * @param v              스키마 버전(1)
 * @param messageId      메시지 ID. 재발행해도 멱등 키가 같으면 한 번만 실행된다
 * @param idempotencyKey 멱등 키({@link ActionIdempotencyKeys}). 1~64자
 * @param kind           행동 종류. 발행 라우팅 키는 {@link ActionKind#routingKey()}
 * @param organizationId 조직 ID
 * @param createdAt      요청 생성 시각(UTC)
 * @param source         출처(BR-ACT-15)
 * @param priority       우선순위. 출처가 정한다(BR-ACT-24)
 * @param validUntil     이 시각이 지나면 실행하지 않는다(FAILED(EXPIRED)). 없으면 조직 기본(600초)
 * @param payload        종류별 본문. COMMAND는 {@link #commandPayload()}
 */
@MessageSchema(name = "action-request", version = 1)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActionRequest(
        int v,
        UUID messageId,
        String idempotencyKey,
        ActionKind kind,
        long organizationId,
        Instant createdAt,
        CommandSource source,
        CommandPriority priority,
        Instant validUntil,
        JsonNode payload) implements Message {

    public static final int VERSION = 1;
    private static final JsonMapper MAPPER = MessageCodec.newMapper();

    public ActionRequest {
        Messages.requireVersion(v);
        Messages.require(messageId, "messageId");
        Messages.requireText(idempotencyKey, "idempotencyKey");
        if (!ActionIdempotencyKeys.isValid(idempotencyKey)) {
            throw new MessageFormatException("idempotencyKey는 1~64자, 허용 문자 A-Z a-z 0-9 . _ : - 여야 합니다");
        }
        Messages.require(kind, "kind");
        Messages.requireId(organizationId, "organizationId");
        Messages.require(createdAt, "createdAt");
        Messages.require(source, "source");
        Messages.require(priority, "priority");
        Messages.require(payload, "payload");
        if (!payload.isObject()) {
            throw new MessageFormatException("payload는 JSON 객체여야 합니다");
        }
    }

    /**
     * 제어 명령 요청을 만든다. 우선순위는 출처로 정한다(BR-ACT-24, 장면 출처는 {@link #of}로 직접 준다).
     *
     * @param validUntil 유효 시각. 없으면 null
     */
    public static ActionRequest command(long organizationId, String idempotencyKey, CommandSource source,
                                        Instant validUntil, CommandPayload payload, Clock clock) {
        return of(ActionKind.COMMAND, organizationId, idempotencyKey, source, source.priority(), validUntil,
                MAPPER.readTree(MAPPER.writeValueAsBytes(payload)), clock);   // 받는 쪽과 같은 숫자 노드 타입
    }

    /** 일반 생성(장면·알림·Sink 등) */
    public static ActionRequest of(ActionKind kind, long organizationId, String idempotencyKey, CommandSource source,
                                   CommandPriority priority, Instant validUntil, JsonNode payload, Clock clock) {
        return new ActionRequest(VERSION, UUID.randomUUID(), idempotencyKey, kind, organizationId, clock.instant(),
                source, priority, validUntil, payload);
    }

    /** {@code data2flow.actions} 발행 라우팅 키 */
    @JsonIgnore
    public String routingKey() {
        return kind.routingKey();
    }

    /** kind=COMMAND의 본문. 다른 종류이거나 모양이 틀리면 {@link MessageFormatException}(DLQ 대상) */
    @JsonIgnore
    public CommandPayload commandPayload() {
        if (kind != ActionKind.COMMAND) {
            throw new MessageFormatException("COMMAND가 아닌 행동 요청입니다: " + kind);
        }
        try {
            return MAPPER.treeToValue(payload, CommandPayload.class);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new MessageFormatException("COMMAND payload를 읽을 수 없습니다: " + e.getMessage(), e);
        }
    }

    /** {@code validUntil}이 지났는지(지나면 FAILED(EXPIRED)) */
    public boolean expiredAt(Instant now) {
        return validUntil != null && now.isAfter(validUntil);
    }
}
