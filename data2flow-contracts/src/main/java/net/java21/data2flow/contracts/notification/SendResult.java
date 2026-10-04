package net.java21.data2flow.contracts.notification;

import java.time.Duration;

/**
 * 발송 결과(OPS-06.06 {@code send}). 공통 계층은 TRANSIENT_FAILURE만 다시 시도한다(BR-RUL-17: 30초·2분·10분·30분·1시간, 최대 5회).
 *
 * @param outcome           결과 분류
 * @param externalMessageId 채널 메시지 ID(메시지 갱신용). 실패면 null
 * @param error             실패 원인(500자 이하, 비밀값 없음). 성공이면 null
 * @param retryAfter        상대가 알려 준 재시도 대기(텔레그램 429 {@code retry_after}). 없으면 null
 */
public record SendResult(Outcome outcome, String externalMessageId, String error, Duration retryAfter) {

    /** 오류 문구 최대 길이(notification_deliveries.last_error) */
    public static final int MAX_ERROR_LENGTH = 500;

    public SendResult {
        if (outcome == null) {
            throw new IllegalArgumentException("outcome은 필수입니다");
        }
        if (outcome != Outcome.SUCCESS && (error == null || error.isBlank())) {
            throw new IllegalArgumentException("실패 결과에는 error가 필요합니다");
        }
        if (error != null && error.length() > MAX_ERROR_LENGTH) {
            error = error.substring(0, MAX_ERROR_LENGTH);
        }
    }

    public static SendResult success(String externalMessageId) {
        return new SendResult(Outcome.SUCCESS, externalMessageId, null, null);
    }

    public static SendResult transientFailure(String error, Duration retryAfter) {
        return new SendResult(Outcome.TRANSIENT_FAILURE, null, error, retryAfter);
    }

    public static SendResult permanentFailure(String error) {
        return new SendResult(Outcome.PERMANENT_FAILURE, null, error, null);
    }

    /** HTTP 상태로 분류: 2xx 성공, 408·425·429·5xx 일시 실패, 그 밖 영구 실패 */
    public static Outcome classifyHttpStatus(int status) {
        if (status >= 200 && status < 300) {
            return Outcome.SUCCESS;
        }
        if (status == 408 || status == 425 || status == 429 || status >= 500) {
            return Outcome.TRANSIENT_FAILURE;
        }
        return Outcome.PERMANENT_FAILURE;
    }

    public boolean retryable() {
        return outcome == Outcome.TRANSIENT_FAILURE;
    }

    public enum Outcome {
        SUCCESS, TRANSIENT_FAILURE, PERMANENT_FAILURE
    }
}
