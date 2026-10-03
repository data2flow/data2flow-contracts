package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * EVT-SIM-04 {@code sim.data.purged}: 가상 데이터 정리가 끝났다(생산 core-api 정리 작업, API-SIM-25). 소비: core-api(알림 센터),
 * analytics(캐시 무효화). 정리 조건은 실행 단위({@code runIds}) 또는 기간({@code from}~{@code to}).
 *
 * @param organizationId 조직 ID
 * @param jobId          정리 작업 ID
 * @param runIds         정리한 실행. 기간 단위면 null
 * @param from           기간 시작. 실행 단위면 null
 * @param to             기간 끝. 실행 단위면 null
 * @param deletedRows    지운 행 수
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SimDataPurged(long organizationId, String jobId, List<Long> runIds, Instant from, Instant to,
                            DeletedRows deletedRows) implements EventPayload {

    public record DeletedRows(long telemetry, long rawMessage, long alarm, long command) {
    }
}
