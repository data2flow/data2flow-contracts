package net.java21.data2flow.contracts.connector;

import java.time.Duration;

/**
 * 폴링 커넥터 공통 규칙(DSC-09.09, BR-DSC-17). 주기는 10초 이상, 실패는 30초·2분·10분 재시도 후 소스 ERROR. 공공 API 일일 한도는
 * 80%에서 경고, 100%에서 다음 날까지 멈춘다(DSC-06.05).
 *
 * @param interval   폴링 주기(10초 이상)
 * @param pageSize   한 번에 가져올 크기(1~10,000)
 * @param dailyQuota 일일 호출 한도. 없으면 0
 */
public record PollingPolicy(Duration interval, int pageSize, int dailyQuota) {

    public static final Duration MIN_INTERVAL = Duration.ofSeconds(10);
    public static final int MAX_PAGE_SIZE = 10_000;
    /** 공공 API 재시도 간격(BR-DSC-17) */
    public static final java.util.List<Duration> RETRY_BACKOFF = java.util.List.of(
            Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10));
    /** 한도 경고 비율 */
    public static final double QUOTA_WARN_RATIO = 0.8;

    public PollingPolicy {
        if (interval == null || interval.compareTo(MIN_INTERVAL) < 0) {
            throw new IllegalArgumentException("폴링 주기는 10초 이상입니다: " + interval);
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE || dailyQuota < 0) {
            throw new IllegalArgumentException("pageSize는 1~10000, dailyQuota는 0 이상입니다");
        }
    }

    /** 오늘 호출 수로 본 한도 상태 */
    public QuotaState quotaState(long callsToday) {
        if (dailyQuota == 0) {
            return QuotaState.OK;
        }
        if (callsToday >= dailyQuota) {
            return QuotaState.EXHAUSTED;
        }
        return callsToday >= Math.ceil(dailyQuota * QUOTA_WARN_RATIO) ? QuotaState.WARN : QuotaState.OK;
    }

    /** {@code attempt}번째(1부터) 실패 뒤 기다릴 시간. 재시도가 끝났으면 빈 값(소스 ERROR) */
    public static java.util.Optional<Duration> retryDelay(int attempt) {
        if (attempt < 1 || attempt > RETRY_BACKOFF.size()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(RETRY_BACKOFF.get(attempt - 1));
    }

    public enum QuotaState {
        OK, WARN, EXHAUSTED
    }
}
