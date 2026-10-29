package net.java21.data2flow.contracts.message.event;

import java.util.Map;

/**
 * EVT-ENE-02 {@code energy.baseline.trained}: 기준선 학습(API-ENE-81)이 끝났다(ENE-01.04, 생산 data2flow-analytics → 소비 core-api 버전 READY·FAILED).
 *
 * @param versionId    {@code data2flow_core.energy_baseline_versions.id}
 * @param status       READY 또는 FAILED
 * @param r2           결정계수(READY일 때)
 * @param coefficients 계수 {@code intercept, cdd, hdd, weekend}(READY일 때)
 * @param residualsRef analytics {@code baseline_models.key}(잔차·예측 조회용)
 * @param warning      적합 경고(예: {@code LOW_FIT} — R² &lt; 0.5)
 * @param error        실패 사유 코드(INSUFFICIENT_DATA, NO_WEATHER, INTERNAL_ERROR)
 * @param neededDays   INSUFFICIENT_DATA일 때 더 필요한 일 수
 * @param trainedDays  학습에 쓴 일 수
 */
public record EnergyBaselineTrained(long versionId, Status status, Double r2, Map<String, Double> coefficients, String residualsRef,
                                    String warning, String error, Integer neededDays, Integer trainedDays) implements EventPayload {

    public enum Status { READY, FAILED }

    public EnergyBaselineTrained {
        if (versionId <= 0) {
            throw new IllegalArgumentException("versionId는 양수입니다");
        }
        if (status == null) {
            throw new IllegalArgumentException("status는 필수입니다");
        }
        coefficients = coefficients == null ? null : Map.copyOf(coefficients);
    }
}
