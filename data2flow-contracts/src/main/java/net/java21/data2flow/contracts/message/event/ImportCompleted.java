package net.java21.data2flow.contracts.message.event;

import java.time.Instant;
import java.util.List;

/**
 * EVT-TSD-02 {@code import.completed}: 과거 데이터 가져오기 끝(TSD-04.02, 생산 core-api → 소비 core-api SSE, analytics 캐시).
 *
 * @param jobId     작업 ID
 * @param inserted  넣은 행
 * @param skipped   건너뛴 행(중복)
 * @param failed    실패 행
 * @param from      데이터 구간 시작
 * @param to        데이터 구간 끝
 * @param deviceIds 영향받은 기기
 */
public record ImportCompleted(String jobId, long inserted, long skipped, long failed, Instant from, Instant to,
                              List<Long> deviceIds) implements EventPayload {

    public ImportCompleted {
        deviceIds = deviceIds == null ? List.of() : List.copyOf(deviceIds);
    }
}
