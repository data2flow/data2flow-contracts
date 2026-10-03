package net.java21.data2flow.contracts.message.event;

/**
 * EVT-TSD-06 {@code partition.warning}: 월 파티션 관리 경고(생산 pipeline 스케줄러, ADR-019).
 *
 * @param table  테이블 이름(예: {@code data2flow_pipeline.telemetry})
 * @param reason {@value #DEFAULT_PARTITION_ROWS}(기본 파티션에 행이 들어옴) 또는 {@value #CREATE_FAILED}(파티션 생성 실패)
 * @param detail 설명
 */
public record PartitionWarning(String table, String reason, String detail) implements EventPayload {

    public static final String DEFAULT_PARTITION_ROWS = "default_partition_rows";
    public static final String CREATE_FAILED = "create_failed";
}
