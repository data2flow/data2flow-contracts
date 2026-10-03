package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * EVT-ING-05 {@code ingest.alert.raised}·{@code ingest.alert.cleared}: 수집 운영 알람(생산 pipeline).
 * 스크립트 오류율 알람(EVT-SCR-02)도 {@code code = SCRIPT_ERROR_RATE}로 이 모양을 쓰고 스크립트 필드를 채운다.
 *
 * @param code         {@code INGEST_LAG}·{@code HEARTBEAT_STALE}·{@code ING_AUTO_REGISTER_QUOTA}·{@code SCRIPT_ERROR_RATE}
 * @param level        WARNING 또는 CRITICAL
 * @param sourceId     관련 소스. 없으면 null
 * @param value        현재 값
 * @param threshold    기준 값
 * @param causeHints   원인 추정(화면 안내용)
 * @param at           판정 시각
 * @param scriptId     (SCRIPT_ERROR_RATE) 스크립트 ID
 * @param versionNo    (SCRIPT_ERROR_RATE) 버전 번호
 * @param errorRate    (SCRIPT_ERROR_RATE) 오류율 0~1
 * @param window       (SCRIPT_ERROR_RATE) 집계 창, 예: {@code 5m}
 * @param autoDisabled (SCRIPT_ERROR_RATE) 자동 비활성 여부
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IngestAlert(String code, Level level, Long sourceId, double value, double threshold,
                          List<String> causeHints, Instant at, Long scriptId, Integer versionNo, Double errorRate,
                          String window, Boolean autoDisabled) implements EventPayload {

    public static final String INGEST_LAG = "INGEST_LAG";
    public static final String HEARTBEAT_STALE = "HEARTBEAT_STALE";
    public static final String AUTO_REGISTER_QUOTA = "ING_AUTO_REGISTER_QUOTA";
    public static final String SCRIPT_ERROR_RATE = "SCRIPT_ERROR_RATE";

    public IngestAlert {
        causeHints = causeHints == null ? List.of() : List.copyOf(causeHints);
    }

    /** 스크립트 필드 없는 수집 알람 */
    public static IngestAlert of(String code, Level level, Long sourceId, double value, double threshold,
                                 List<String> causeHints, Instant at) {
        return new IngestAlert(code, level, sourceId, value, threshold, causeHints, at, null, null, null, null, null);
    }

    public enum Level {
        WARNING, CRITICAL
    }
}
