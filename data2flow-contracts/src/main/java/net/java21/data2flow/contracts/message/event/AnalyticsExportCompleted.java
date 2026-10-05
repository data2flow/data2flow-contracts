package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * EVT-ANA-06 {@code analytics.export.completed}: PDF 내보내기(202 작업)가 끝났다(API-ANA-…, 내보내기). 생산 data2flow-analytics →
 * 소비 core-api(요청자 알림 센터).
 *
 * <p>analytics는 {@code userId}를 요청자가 없을 때도 {@code null}로 싣고, 문서 필드 뒤에 내보내기 ID {@code exportId}를 더 싣는다.
 *
 * @param exportJobId 내보내기 작업 ID(문자열)
 * @param userId      요청자 사용자 ID(문자열). 없으면 null
 * @param downloadUrl 내려받기 경로({@code /bff/download/{exportId}})
 * @param expiresAt   내려받기 만료 시각(24시간)
 * @param exportId    내보내기 ID. 없을 수 있다
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalyticsExportCompleted(String exportJobId, @JsonInclude(JsonInclude.Include.ALWAYS) String userId, String downloadUrl,
                                       Instant expiresAt, String exportId) implements EventPayload {

    public AnalyticsExportCompleted {
        if (exportJobId == null || downloadUrl == null || expiresAt == null) {
            throw new IllegalArgumentException("exportJobId·downloadUrl·expiresAt은 필수입니다");
        }
    }

    /** 요청자 ID를 숫자로. 없거나 숫자가 아니면 null */
    @JsonIgnore
    public Long userIdAsLong() {
        return AnalyticsIds.toLong(userId);
    }
}
