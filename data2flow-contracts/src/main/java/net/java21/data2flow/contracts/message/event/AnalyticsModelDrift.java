package net.java21.data2flow.contracts.message.event;

import java.time.Instant;

/**
 * EVT-ANA-04 {@code analytics.model.drift}: 학습 모델의 입력 분포가 달라졌다(PSI &gt; 0.2). 생산 data2flow-analytics(모델 수명 주기 점검) →
 * 소비 core-api(모델 관리 화면·알림 센터).
 *
 * @param analysisId 분석 ID(문자열)
 * @param modelId    모델 ID(문자열)
 * @param psi        PSI(소수 6자리)
 * @param detectedAt 감지 시각
 */
public record AnalyticsModelDrift(String analysisId, String modelId, double psi, Instant detectedAt) implements EventPayload {

    public AnalyticsModelDrift {
        if (analysisId == null || modelId == null || detectedAt == null) {
            throw new IllegalArgumentException("analysisId·modelId·detectedAt은 필수입니다");
        }
    }
}
