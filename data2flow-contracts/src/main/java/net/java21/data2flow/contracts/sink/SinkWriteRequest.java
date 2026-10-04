package net.java21.data2flow.contracts.sink;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sink 쓰기 요청: {@code ActionRequest(kind=SINK)}의 {@code payload}(ACT-api §5.1) → {@code action.sinks}. 생산은 flow-engine Sink 노드
 * ({@code sink.database}, TC-FLW-058: 레코드 배열 입력을 배치로 묶어 요청 하나), 소비는 action {@code sink} 패키지(ADR-025)다.
 *
 * <p>노드가 필드 매핑을 적용한 뒤 보내므로 {@code records}의 키는 대상 열·필드 이름이다. 레코드 값은 JSON 원시값이고 시각은 ISO-8601 UTC
 * 문자열이다. 멱등 키는 행동 요청의 {@code idempotencyKey}(BR-FLW-13, 배치가 여러 개면 분할 인덱스 포함)로, action은 같은 키를 한 번만
 * 쓴다(NFR-02.11). 실패한 배치는 dead-letter에 24시간 보관한다(BR-FLW-28).
 *
 * <pre>{@code
 * {"connectionId":4,"target":"room_temp","mode":"UPSERT","upsertKeys":["device_id","ts"],
 *  "records":[{"device_id":15,"ts":"2026-10-03T01:12:00Z","temperature":27.4}],"batchIndex":0}
 * }</pre>
 *
 * @param connectionId Sink 연결 ID(sink_connections.id)
 * @param target       대상 테이블 또는 measurement
 * @param mode         쓰기 모드
 * @param upsertKeys   UPSERT 키 열(UPSERT면 1개 이상)
 * @param records      레코드(1~{@value #MAX_RECORDS}건)
 * @param batchIndex   한 트리거에서 나뉜 배치 순번(0부터). 하나면 null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SinkWriteRequest(long connectionId, String target, SinkMode mode, List<String> upsertKeys,
                               List<Map<String, Object>> records, Integer batchIndex) {

    /** 요청 하나의 최대 레코드 수(ACT-api §5.1) */
    public static final int MAX_RECORDS = 1000;
    /** 노드 기본 배치 크기(BR-FLW-28: 100건 또는 1초) */
    public static final int DEFAULT_BATCH_SIZE = 100;

    public SinkWriteRequest {
        if (connectionId < 1) {
            throw new IllegalArgumentException("sink.connectionId는 1 이상입니다");
        }
        if (target == null || target.isBlank() || mode == null) {
            throw new IllegalArgumentException("sink.target·mode는 필수입니다");
        }
        upsertKeys = upsertKeys == null ? List.of() : List.copyOf(upsertKeys);
        if (mode == SinkMode.UPSERT && upsertKeys.isEmpty()) {
            throw new IllegalArgumentException("UPSERT에는 upsertKeys가 필요합니다");
        }
        if (records == null || records.isEmpty() || records.size() > MAX_RECORDS) {
            throw new IllegalArgumentException("sink.records는 1~" + MAX_RECORDS + "건입니다");
        }
        List<Map<String, Object>> copy = new ArrayList<>(records.size());
        for (Map<String, Object> r : records) {
            if (r == null || r.isEmpty()) {
                throw new IllegalArgumentException("빈 레코드는 쓸 수 없습니다");
            }
            copy.add(Map.copyOf(r));
        }
        records = List.copyOf(copy);
        if (batchIndex != null && batchIndex < 0) {
            throw new IllegalArgumentException("batchIndex는 0 이상입니다");
        }
        if (mode == SinkMode.UPSERT) {
            for (Map<String, Object> r : records) {
                for (String key : upsertKeys) {
                    if (!r.containsKey(key)) {
                        throw new IllegalArgumentException("UPSERT 레코드에 키 열이 없습니다: " + key);
                    }
                }
            }
        }
    }

    /**
     * 레코드 배열을 배치 크기로 나눠 요청 목록을 만든다(배치가 둘 이상이면 {@code batchIndex} 0, 1, …). 멱등 키는 배치마다
     * {@code ActionIdempotencyKeys.flow(flowId, nodeId, triggerMessageId, batchIndex)}로 만든다.
     */
    public static List<SinkWriteRequest> batches(long connectionId, String target, SinkMode mode, List<String> upsertKeys,
                                                 List<Map<String, Object>> records, int batchSize) {
        if (batchSize < 1 || batchSize > MAX_RECORDS) {
            throw new IllegalArgumentException("batchSize는 1~" + MAX_RECORDS + "입니다");
        }
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        int count = (records.size() + batchSize - 1) / batchSize;
        List<SinkWriteRequest> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            List<Map<String, Object>> part = records.subList(i * batchSize, Math.min(records.size(), (i + 1) * batchSize));
            out.add(new SinkWriteRequest(connectionId, target, mode, upsertKeys, part, count == 1 ? null : i));
        }
        return List.copyOf(out);
    }
}
