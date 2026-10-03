package net.java21.data2flow.contracts.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.java21.data2flow.contracts.identity.CurrentUser;
import net.java21.data2flow.contracts.secret.SecretMasker;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 감사 기록 한 건(IAM-06.01, domain-model §2.8, API-IAM-39 본문). 열 이름은 {@code data2flow_core.audit_logs}와 같다.
 *
 * <p>만들 때 {@code detail}의 비밀값을 {@code ***}로 가린다(BR-IAM-21, AT-IAM-13.3). 그래서 어떤 서비스가 기록해도
 * 저장소·외부 전달(IAM-06.05)에 평문이 들어가지 않는다. 길이 제한이 있는 이름·User-Agent는 잘라서 넣는다.
 *
 * @param organizationId 조직(필수, BR-IAM-01)
 * @param occurredAt     일어난 시각(UTC)
 * @param actorType      행위자 종류
 * @param actorId        사용자 ID, 서비스 계정·토큰 ID, flowId 등(최대 64자)
 * @param actorName      기록 시점 이름 스냅샷(최대 100자, 익명화 뒤에도 의미 유지)
 * @param action         UPPER_SNAKE action 코드(최대 60자)
 * @param targetType     대상 종류(USER, DEVICE, FLOW …, 최대 40자)
 * @param targetId       대상 ID(최대 64자)
 * @param result         SUCCESS / FAILURE / DENIED
 * @param detail         변경 전후 값 등(비밀값은 가려짐)
 * @param cause          자동 제어 원인(IAM-06.04)
 * @param ip             요청 IP
 * @param userAgent      User-Agent(최대 300자)
 * @param requestId      X-REQUEST-ID(NFR-07.01)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditEvent(long organizationId, Instant occurredAt, AuditActorType actorType, String actorId, String actorName,
                         String action, String targetType, String targetId, AuditResult result, Map<String, Object> detail,
                         AuditCause cause, String ip, String userAgent, String requestId) {

    private static final Pattern ACTION = Pattern.compile("[A-Z][A-Z0-9_]{1,59}");
    static final String MDC_REQUEST_ID = "requestId";

    public AuditEvent {
        if (organizationId <= 0) {
            throw new IllegalArgumentException("감사 기록에는 조직 ID가 필요합니다");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(actorType, "actorType");
        Objects.requireNonNull(result, "result");
        if (action == null || !ACTION.matcher(action).matches()) {
            throw new IllegalArgumentException("action은 UPPER_SNAKE 60자 이내여야 합니다: " + action);
        }
        actorId = cut(actorId, 64);
        actorName = cut(actorName, 100);
        targetType = cut(targetType, 40);
        targetId = cut(targetId, 64);
        userAgent = cut(userAgent, 300);
        requestId = cut(requestId, 64);
        detail = detail == null || detail.isEmpty() ? null : Collections.unmodifiableMap(SecretMasker.maskMap(detail));
    }

    public static Builder builder(long organizationId, String action) {
        return new Builder(organizationId, action);
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    /** 감사 기록 빌더. 기본값: 결과 SUCCESS, 시각 지금, 요청 ID는 로그 MDC({@code requestId}) */
    public static final class Builder {
        private final long organizationId;
        private final String action;
        private Instant occurredAt;
        private AuditActorType actorType = AuditActorType.SYSTEM;
        private String actorId;
        private String actorName;
        private String targetType;
        private String targetId;
        private AuditResult result = AuditResult.SUCCESS;
        private final Map<String, Object> detail = new LinkedHashMap<>();
        private AuditCause cause;
        private String ip;
        private String userAgent;
        private String requestId = MDC.get(MDC_REQUEST_ID);

        private Builder(long organizationId, String action) {
            this.organizationId = organizationId;
            this.action = action;
        }

        public Builder occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public Builder actor(AuditActorType type, String id, String name) {
            this.actorType = type;
            this.actorId = id;
            this.actorName = name;
            return this;
        }

        /** 요청 사용자를 행위자로. 장기 토큰 요청이면 detail에 {@code accessTokenId}를 남긴다("MCP(토큰 id)") */
        public Builder actor(CurrentUser user) {
            this.actorType = AuditActorType.USER;
            this.actorId = Long.toString(user.userId());
            if (user.viaAccessToken()) {
                detail.put("accessTokenId", Long.toString(user.accessTokenId()));
            }
            return this;
        }

        public Builder target(String type, String id) {
            this.targetType = type;
            this.targetId = id;
            return this;
        }

        public Builder result(AuditResult result) {
            this.result = result;
            return this;
        }

        public Builder detail(String key, Object value) {
            detail.put(key, value);
            return this;
        }

        public Builder detail(Map<String, ?> values) {
            detail.putAll(values);
            return this;
        }

        public Builder cause(AuditCause cause) {
            this.cause = cause;
            return this;
        }

        public Builder ip(String ip) {
            this.ip = ip;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public AuditEvent build() {
            return new AuditEvent(organizationId, occurredAt == null ? Instant.now() : occurredAt, actorType, actorId,
                    actorName, action, targetType, targetId, result, detail, cause, ip, userAgent, requestId);
        }
    }
}
