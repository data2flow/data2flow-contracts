package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

import java.time.Instant;
import java.util.Map;

/**
 * EVT-CMP-02 {@code compliance.score.updated}: 공간의 종합 실내 환경 점수를 새로 계산했다(CMP-01.05, BR-CMP-08, 생산 core-api 5분 주기 →
 * 소비 core-api SSE 점수 카드·공개 화면, data2flow-analytics). 공간당 5분 1건이다.
 *
 * @param spaceId          공간 ID
 * @param time             5분 경계 시각(UTC)
 * @param score            0~100. 계산에 쓸 항목이 하나도 없으면 null
 * @param grade            점수 등급. score가 null이면 null
 * @param indices          항목별 지수 1~5(최근 10분 데이터가 없는 항목은 빠진다)
 * @param topPenaltyMetric 감점이 가장 큰 항목(w·(5−i) 최대). 감점이 없으면 null
 * @param basis            PROFILE(적용된 프로필 기준) 또는 DEFAULT(조직·시스템 기본 기준, BR-CMP-09)
 */
public record ComplianceScoreUpdated(long spaceId, Instant time, Integer score, Grade grade, Map<String, Integer> indices, String topPenaltyMetric,
                                     Basis basis) implements EventPayload {

    public ComplianceScoreUpdated {
        if (time == null || basis == null) {
            throw new IllegalArgumentException("time·basis는 필수입니다");
        }
        if (score != null && (score < 0 || score > 100)) {
            throw new IllegalArgumentException("score는 0~100입니다");
        }
        indices = indices == null ? Map.of() : Map.copyOf(indices);
    }

    /** 점수 등급. 모르는 값은 UNKNOWN(하위 호환) */
    public enum Grade {
        GOOD, MODERATE, POOR, BAD,
        @JsonEnumDefaultValue
        UNKNOWN
    }

    /** 점수 기준의 출처 */
    public enum Basis {
        PROFILE, DEFAULT,
        @JsonEnumDefaultValue
        UNKNOWN
    }
}
