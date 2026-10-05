package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * EVT-AIA-03 {@code ai.quota.exceeded}: AI 일일 사용량 한도에 닿았다(AIA-07.04, BR-AIA-08). 생산 data2flow-ai → 소비 core-api(관리자 알림 센터).
 * ai는 같은 한도(조직·사용자·종류)마다 하루(조직 시간대 자정까지) 한 번만 낸다.
 *
 * @param scope     ORG(조직 한도) 또는 USER(사용자 한도)
 * @param userId    사용자 한도면 그 사용자 ID. 조직 한도면 null
 * @param limitType REQUESTS(일 요청 수) 또는 TOKENS(일 토큰 수)
 * @param resetAt   한도가 풀리는 시각(조직 시간대 다음 자정, UTC)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiQuotaExceeded(Scope scope, Long userId, LimitType limitType, Instant resetAt) implements EventPayload {

    public enum Scope {
        ORG, USER,
        @JsonEnumDefaultValue
        UNKNOWN
    }

    public enum LimitType {
        REQUESTS, TOKENS,
        @JsonEnumDefaultValue
        UNKNOWN
    }

    public AiQuotaExceeded {
        if (scope == null || limitType == null || resetAt == null) {
            throw new IllegalArgumentException("scope·limitType·resetAt은 필수입니다");
        }
        if (scope == Scope.USER && userId == null) {
            throw new IllegalArgumentException("사용자 한도는 userId가 필요합니다");
        }
        if (scope == Scope.ORG && userId != null) {
            throw new IllegalArgumentException("조직 한도에는 userId를 싣지 않습니다");
        }
    }

    /** 조직 한도 */
    public static AiQuotaExceeded organization(LimitType limitType, Instant resetAt) {
        return new AiQuotaExceeded(Scope.ORG, null, limitType, resetAt);
    }

    /** 사용자 한도 */
    public static AiQuotaExceeded user(long userId, LimitType limitType, Instant resetAt) {
        return new AiQuotaExceeded(Scope.USER, userId, limitType, resetAt);
    }
}
