package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * EVT-DSC-10 {@code edge.status.changed}·{@code edge.config.applied}·{@code edge.buffer.dropped}: 엣지 게이트웨이 상태(DSC-08.02·08.03,
 * 생산 ingress 엣지 API 수신 측 → 소비 core-api SSE·알람). 종류마다 채우는 칸이 다르다.
 *
 * @param edgeId        엣지 ID
 * @param status        (status.changed) 새 상태: REGISTERING·ONLINE·OFFLINE·UPDATING·ERROR·REVOKED
 * @param configVersion (config.applied) 적용한 설정 버전
 * @param result        (config.applied) APPLIED·FAILED_ROLLED_BACK
 * @param droppedItems  (buffer.dropped) 버퍼 한도(7일·2GB)로 버린 항목 수
 * @param at            시각
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EdgeEvent(long edgeId, String status, Integer configVersion, String result, Long droppedItems, Instant at)
        implements EventPayload {

    public static EdgeEvent statusChanged(long edgeId, String status, Instant at) {
        return new EdgeEvent(edgeId, status, null, null, null, at);
    }

    public static EdgeEvent configApplied(long edgeId, int configVersion, String result, Instant at) {
        return new EdgeEvent(edgeId, null, configVersion, result, null, at);
    }

    public static EdgeEvent bufferDropped(long edgeId, long droppedItems, Instant at) {
        return new EdgeEvent(edgeId, null, null, null, droppedItems, at);
    }
}
