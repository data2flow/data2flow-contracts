package net.java21.data2flow.contracts.message.event;

import java.time.Instant;
import java.util.List;

/**
 * EVT-TSD-03 {@code aggregates.recomputed}: 늦은 데이터·재처리로 집계 구간을 다시 계산했다(생산 pipeline, TSD-02.02).
 * core-api는 조회 캐시를 무효화한다(BR-TSD-20).
 *
 * @param level 집계 단위({@code 1m}·{@code 1h}·{@code 1d})
 * @param items 다시 계산한 (기기, 측정 키, 구간)
 */
public record AggregatesRecomputed(String level, List<Item> items) implements EventPayload {

    public AggregatesRecomputed {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record Item(long deviceId, String metricKey, Instant from, Instant to) {
    }
}
