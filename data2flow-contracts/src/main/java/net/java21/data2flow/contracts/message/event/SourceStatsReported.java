package net.java21.data2flow.contracts.message.event;

import java.time.Instant;
import java.util.Map;

/**
 * EVT-DSC-03 {@code source.stats.1m}: 소스별 1분 처리 건수. ingress는 수신 수, pipeline은 디코딩·스크립트 오류 수를 보낸다.
 * core-api가 {@code source_stat_1m}에 합산한다.
 *
 * @param sourceId 데이터 소스 ID
 * @param minute   집계 구간 시작(분 단위로 자른 UTC 시각)
 * @param producer 보낸 서비스
 * @param counters 지표 이름 → 건수(예: {@code received}, {@code decodeErrors})
 */
public record SourceStatsReported(long sourceId, Instant minute, Producer producer, Map<String, Long> counters)
        implements EventPayload {

    public SourceStatsReported {
        counters = counters == null ? Map.of() : Map.copyOf(counters);
    }

    public enum Producer {
        INGRESS, PIPELINE
    }
}
