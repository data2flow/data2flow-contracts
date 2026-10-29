package net.java21.data2flow.contracts.message.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * EVT-CMP-04 {@code compliance.excursion.started}·{@code compliance.excursion.ended}: 공간의 측정 항목이 프로필 기준(기준 등급)을 벗어나기
 * 시작했거나 기준 안으로 돌아왔다(CMP-02.01, 생산 core-api → 소비 OCC 구독 알림, flow-engine 트리거, core-api SSE). 시작이면 {@code endedAt}이
 * null이다.
 *
 * @param excursionId      이탈 구간 ID
 * @param spaceId          공간 ID
 * @param metricKey        측정 항목
 * @param profileVersionId 판정에 쓴 프로필 버전 ID
 * @param startedAt        이탈 시작 시각(UTC)
 * @param endedAt          이탈이 끝난 시각. 진행 중이면 null
 * @param peakValue        구간에서 기준을 가장 크게 벗어난 값(평가 단위 평균)
 */
public record ComplianceExcursionChanged(long excursionId, long spaceId, String metricKey, long profileVersionId, Instant startedAt,
                                         Instant endedAt, BigDecimal peakValue) implements EventPayload {

    public ComplianceExcursionChanged {
        if (metricKey == null || metricKey.isBlank() || startedAt == null || peakValue == null) {
            throw new IllegalArgumentException("metricKey·startedAt·peakValue는 필수입니다");
        }
        if (endedAt != null && endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("endedAt은 startedAt 이후여야 합니다");
        }
    }
}
