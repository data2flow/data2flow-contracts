package net.java21.data2flow.contracts.idempotency;

/**
 * 키를 잡으려 한 결과.
 *
 * @param outcome  결과
 * @param response {@link Outcome#REPLAY}일 때 처음 응답
 */
public record IdempotencyClaim(Outcome outcome, StoredResponse response) {

    public enum Outcome {
        /** 처음 온 키(또는 만료·중단된 키를 넘겨받음). 처리하고 결과를 저장한다 */
        ACQUIRED,
        /** 같은 요청이 이미 끝났다. 처음 응답을 돌려준다 */
        REPLAY,
        /** 같은 요청이 처리 중이다. 409 IDEMPOTENCY_CONFLICT */
        IN_PROGRESS,
        /** 같은 키에 다른 요청이다. 409 IDEMPOTENCY_KEY_REUSED */
        MISMATCH
    }

    public static IdempotencyClaim acquired() {
        return new IdempotencyClaim(Outcome.ACQUIRED, null);
    }

    public static IdempotencyClaim replay(StoredResponse response) {
        return new IdempotencyClaim(Outcome.REPLAY, response);
    }

    public static IdempotencyClaim inProgress() {
        return new IdempotencyClaim(Outcome.IN_PROGRESS, null);
    }

    public static IdempotencyClaim mismatch() {
        return new IdempotencyClaim(Outcome.MISMATCH, null);
    }
}
