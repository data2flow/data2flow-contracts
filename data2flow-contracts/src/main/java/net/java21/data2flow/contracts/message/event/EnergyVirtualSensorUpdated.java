package net.java21.data2flow.contracts.message.event;

import java.time.Instant;
import java.util.List;

/**
 * EVT-ENE-03 {@code energy.virtual-sensor.updated}: 공간 가상 센서 값을 다시 계산했다(ENE-02, 생산 data2flow-analytics → 소비 core-api 마지막 계산 시각·상태).
 *
 * @param spaceId      {@code data2flow_core.spaces.id}
 * @param kind         ACH, OCCUPANCY, THERMAL_COMFORT
 * @param metricKeys   계산한 측정 항목(예: {@code ach, ventilation_m3h})
 * @param computedFrom 계산한 구간 시작
 * @param computedTo   계산한 구간 끝
 * @param status       OK 또는 INPUT_MISSING(입력 항목 없음)
 * @param missing      INPUT_MISSING일 때 없는 입력 측정 항목
 */
public record EnergyVirtualSensorUpdated(long spaceId, Kind kind, List<String> metricKeys, Instant computedFrom, Instant computedTo, Status status,
                                         List<String> missing) implements EventPayload {

    public enum Kind { ACH, OCCUPANCY, THERMAL_COMFORT }

    public enum Status { OK, INPUT_MISSING }

    public EnergyVirtualSensorUpdated {
        if (spaceId <= 0) {
            throw new IllegalArgumentException("spaceId는 양수입니다");
        }
        if (kind == null || status == null) {
            throw new IllegalArgumentException("kind·status는 필수입니다");
        }
        metricKeys = metricKeys == null ? List.of() : List.copyOf(metricKeys);
        missing = missing == null ? null : List.copyOf(missing);
    }
}
