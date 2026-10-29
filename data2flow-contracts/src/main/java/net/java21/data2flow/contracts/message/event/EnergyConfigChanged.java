package net.java21.data2flow.contracts.message.event;

/**
 * EVT-ENE-07 {@code energy.config.changed}: 조직의 에너지 설정이 바뀌었다(ENE-01·02·05, 생산 core-api → 소비 data2flow-analytics).
 * analytics는 받은 조직의 설정 묶음(API-ENE-70) 캐시를 지운다. 과거 집계는 다시 계산하지 않는다(BR-ENE-05).
 *
 * @param scope   바뀐 설정 종류
 * @param spaceId 공간 단위 설정이면 그 공간(사이트 단가·계량 원천·가상 센서), 조직 전체면 null
 */
public record EnergyConfigChanged(Scope scope, Long spaceId) implements EventPayload {

    public enum Scope {
        TARIFF, EMISSION, SOURCE, VIRTUAL_SENSOR, FDD_RULE, DETECTION
    }

    public EnergyConfigChanged {
        if (scope == null) {
            throw new IllegalArgumentException("scope는 필수입니다");
        }
        if (spaceId != null && spaceId < 1) {
            throw new IllegalArgumentException("spaceId는 1 이상입니다");
        }
    }
}
