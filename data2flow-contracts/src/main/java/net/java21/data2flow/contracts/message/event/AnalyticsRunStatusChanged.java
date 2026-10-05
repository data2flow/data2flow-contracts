package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * EVT-ANA-01 {@code analytics.run.{status}}: 분석 실행 상태가 바뀌었다(ANA-api §3). 생산 data2flow-analytics(Python, 워커
 * {@code RunRunner._publish_run}) → 소비 core-api(색인의 최근 실행·SSE 실행 스트림·위젯 갱신), ai(성공 시 자동 해설).
 *
 * <p>analytics가 실제로 보내는 JSON과 같은 모양이다. ID는 문자열이고, {@code progress}·{@code stage}는 값이 없어도 {@code null}로
 * 늘 실리며, {@code errorCode}·{@code finishedAt}은 있을 때만 실린다.
 *
 * @param runId      실행 ID(문자열)
 * @param analysisId 분석 ID(문자열)
 * @param status     실행 상태. 라우팅 키 끝은 이 값의 소문자다
 * @param progress   진행률 0~100. 모르면 null
 * @param trigger    MANUAL·SCHEDULE·API
 * @param stage      실행 단계(LOAD·…·SAVE). 없으면 null
 * @param errorCode  실패 코드(실패·시간 초과일 때)
 * @param finishedAt 끝난 시각(끝났을 때)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalyticsRunStatusChanged(String runId, String analysisId, Status status,
                                        @JsonInclude(JsonInclude.Include.ALWAYS) Integer progress, Trigger trigger,
                                        @JsonInclude(JsonInclude.Include.ALWAYS) String stage, String errorCode,
                                        Instant finishedAt) implements EventPayload {

    /** 실행 상태(analytics {@code runs.status}) */
    public enum Status {
        QUEUED, PENDING, RUNNING, SUCCEEDED, FAILED, TIMEOUT, CANCELLED,
        @JsonEnumDefaultValue
        UNKNOWN;

        /** 라우팅 키 끝({@code analytics.run.succeeded}의 succeeded) */
        public String routingSuffix() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /** 다시 바뀌지 않는 끝 상태인가 */
        public boolean terminal() {
            return this == SUCCEEDED || this == FAILED || this == TIMEOUT || this == CANCELLED;
        }
    }

    /** 실행 계기 */
    public enum Trigger {
        MANUAL, SCHEDULE, API,
        @JsonEnumDefaultValue
        UNKNOWN
    }

    public AnalyticsRunStatusChanged {
        if (runId == null || runId.isBlank() || analysisId == null || analysisId.isBlank() || status == null || trigger == null) {
            throw new IllegalArgumentException("runId·analysisId·status·trigger는 필수입니다");
        }
    }

    /** 실행 ID를 숫자로. 숫자가 아니면 null */
    @JsonIgnore
    public Long runIdAsLong() {
        return AnalyticsIds.toLong(runId);
    }

    /** 분석 ID를 숫자로. 숫자가 아니면 null */
    @JsonIgnore
    public Long analysisIdAsLong() {
        return AnalyticsIds.toLong(analysisId);
    }
}
