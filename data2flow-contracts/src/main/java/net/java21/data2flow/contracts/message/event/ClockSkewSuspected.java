package net.java21.data2flow.contracts.message.event;

import java.time.Instant;

/**
 * EVT-ING-06 {@code ingest.clock-skew.suspected}: 기기 시계 오차가 의심된다.
 *
 * @param deviceId   기기 ID
 * @param avgSkewSec 측정 시각 − 수신 시각 평균(초)
 * @param since      의심이 시작된 시각
 */
public record ClockSkewSuspected(long deviceId, double avgSkewSec, Instant since) implements EventPayload {
}
