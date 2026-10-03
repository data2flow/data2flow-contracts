package net.java21.data2flow.contracts.messaging;

import java.time.Duration;

/**
 * Super Stream 하나의 정의(architecture.md §4.2). 생산자 서비스가 시작할 때 없으면 이 값으로 만든다.
 *
 * @param name               Super Stream 이름
 * @param partitions         파티션 수(소비자 인스턴스는 이 수까지만 의미가 있다)
 * @param maxAge             보관 기간
 * @param maxBytesPerPartition 파티션당 보관 용량 상한(바이트)
 */
public record SuperStreamSpec(String name, int partitions, Duration maxAge, long maxBytesPerPartition) {

    private static final long GB = 1024L * 1024 * 1024;

    /** {@code data2flow.raw}: 12 파티션, 7일 또는 파티션당 10GB */
    public static final SuperStreamSpec RAW =
            new SuperStreamSpec(MessagingNames.STREAM_RAW, 12, Duration.ofDays(7), 10 * GB);

    /** {@code data2flow.telemetry}: 12 파티션, 3일 또는 파티션당 5GB */
    public static final SuperStreamSpec TELEMETRY =
            new SuperStreamSpec(MessagingNames.STREAM_TELEMETRY, 12, Duration.ofDays(3), 5 * GB);

    /** 파티션 스트림 이름 {@code {name}-{index}}(RabbitMQ Super Stream 규칙) */
    public String partition(int index) {
        if (index < 0 || index >= partitions) {
            throw new IllegalArgumentException("파티션 번호는 0~" + (partitions - 1) + "입니다: " + index);
        }
        return name + "-" + index;
    }
}
