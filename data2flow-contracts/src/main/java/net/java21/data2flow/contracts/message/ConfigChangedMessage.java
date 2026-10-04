package net.java21.data2flow.contracts.message;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * 설정 변경 메시지 봉투 v1(architecture.md §4.5 정본, domain-map §6-6). fanout {@code data2flow.config}의 모든 메시지가 이 모양이다.
 *
 * <p>EVT-DEV-04(DEVICE·MODEL·METRIC·ALIAS·SPACE·GROUP·ATTRIBUTE), EVT-DSC-01(SOURCE·OUTPUT·CREDENTIAL), EVT-SCR-01(SCRIPT),
 * EVT-FLW-04, EVT-OPS-04, EVT-ING-08(INGEST_CACHE), ACT(CAPABILITY·DRIVER·INTERLOCK), FLW-04(SINK_CONNECTION),
 * RUL·OPS-06 알림(NOTIFICATION_CHANNEL·NOTIFICATION_POLICY·NOTIFICATION_TEMPLATE·SILENCE·ON_CALL·NOTIFY_PREFERENCE), DSC-08(EDGE) 등. 받은 서비스는 내용이 아니라 {@code entityType}·{@code id}·{@code version}만 보고
 * 캐시를 무효화한 뒤 DB(내부 API)에서 다시 읽는다. 원천은 DB이고, 재연결하면 전체를 다시 읽어 놓친 메시지를 보완한다.
 *
 * @param v          스키마 버전(1)
 * @param messageId  메시지 ID
 * @param entityType 바뀐 대상 종류. 이 코드가 모르는 값은 {@link EntityType#UNKNOWN}(무시)
 * @param id         대상 ID(문자열)
 * @param version    대상의 새 버전
 * @param op         UPSERT 또는 DELETE
 * @param orgId      조직 ID(문자열)
 * @param at         변경 시각(UTC)
 */
@MessageSchema(name = "config-changed", version = 1)
public record ConfigChangedMessage(
        int v,
        UUID messageId,
        EntityType entityType,
        String id,
        long version,
        Op op,
        String orgId,
        Instant at) implements Message {

    public static final int VERSION = 1;

    public ConfigChangedMessage {
        Messages.requireVersion(v);
        Messages.require(messageId, "messageId");
        Messages.require(entityType, "entityType");
        Messages.requireText(id, "id");
        Messages.require(op, "op");
        Messages.requireText(orgId, "orgId");
        Messages.require(at, "at");
    }

    public static ConfigChangedMessage upsert(EntityType type, long id, long version, long organizationId, Clock clock) {
        return new ConfigChangedMessage(VERSION, UUID.randomUUID(), type, Long.toString(id), version, Op.UPSERT,
                Long.toString(organizationId), clock.instant());
    }

    public static ConfigChangedMessage delete(EntityType type, long id, long version, long organizationId, Clock clock) {
        return new ConfigChangedMessage(VERSION, UUID.randomUUID(), type, Long.toString(id), version, Op.DELETE,
                Long.toString(organizationId), clock.instant());
    }

    /** 바뀐 대상 종류(architecture.md §4.5) */
    public enum EntityType {
        DEVICE, MODEL, METRIC, ALIAS, SPACE, GROUP, ATTRIBUTE,
        SOURCE, OUTPUT, CREDENTIAL,
        SCRIPT,
        FLOW, OVERLAY, VARIABLE, SUBFLOW, EMERGENCY_STOP,
        SETTING, LOG_LEVEL, FEATURE_FLAG,
        INGEST_CACHE,
        OCC_PUBLIC,
        SIM_SANDBOX,
        /** 사용자 정의 기능 정의(ACT-01.04, id = 기능 이름). action은 그 기능을 쓰는 제어 프로필만 지운다 */
        CAPABILITY,
        /** 드라이버 설정(ACT-03.05, id = 드라이버 ID). action은 그 드라이버에 연결된 제어 프로필만 지운다 */
        DRIVER,
        /** 인터락 규칙(ACT-06.02, id = 인터락 ID). action은 그 공간의 인터락 캐시를 지운다 */
        INTERLOCK,
        /** Sink 연결(FLW-04.01, id = 연결 ID). action은 연결 풀을 다시 만든다 */
        SINK_CONNECTION,
        /** 알림 채널(OPS-06.01, id = 채널 ID). action은 채널 설정·비밀값을 다시 읽는다 */
        NOTIFICATION_CHANNEL,
        /** 알림 정책(RUL-03.02, id = 정책 ID). 플로우 알림 노드의 정책 수신자 계산 캐시 */
        NOTIFICATION_POLICY,
        /** 알림 템플릿(RUL-05.01, id = 템플릿 ID) */
        NOTIFICATION_TEMPLATE,
        /** 무음(RUL-02.07, id = 무음 ID) */
        SILENCE,
        /** 당직표·대리 근무(RUL-05.03, id = 당직표 ID) */
        ON_CALL,
        /** 사용자 알림 수신 설정·방해 금지·메신저 연결(OPS-06.05, RUL-05.04, id = 사용자 ID) */
        NOTIFY_PREFERENCE,
        /** 엣지 게이트웨이 설정(DSC-08.03, id = 엣지 ID) */
        EDGE,
        /** 이 코드보다 새 생산자가 보낸 종류. 소비자는 무시한다 */
        @JsonEnumDefaultValue
        UNKNOWN
    }

    public enum Op {
        UPSERT, DELETE
    }
}
