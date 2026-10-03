package net.java21.data2flow.contracts.message.event;

import java.time.Instant;

/**
 * EVT-ING-06 {@code ingest.gap.detected}: 기기 수신 공백(예상 주기의 3배 이상, ING-06.05).
 *
 * @param deviceId         기기 ID
 * @param from             공백 시작(마지막 수신)
 * @param to               공백 끝(다시 받은 시각 또는 판정 시각)
 * @param estimatedMissing 빠졌다고 추정한 메시지 수
 */
public record IngestGapDetected(long deviceId, Instant from, Instant to, int estimatedMissing) implements EventPayload {
}
