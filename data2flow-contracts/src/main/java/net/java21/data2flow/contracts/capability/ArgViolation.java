package net.java21.data2flow.contracts.capability;

import net.java21.data2flow.contracts.error.FieldErrorDetail;

/**
 * 명령 검증 위반 하나.
 *
 * @param field   위반 위치: {@code capability}, {@code command}, {@code args}, {@code args.<속성>}
 * @param reason  위반 종류. {@link Reason#resultCode()}가 API 오류 코드다
 * @param message 개발자용 설명(한국어). 화면 문구는 서비스가 resultCode로 현지화한다
 * @param min     범위 위반이면 적용한 최솟값(문구 {min}). 아니면 null
 * @param max     범위 위반이면 적용한 최댓값(문구 {max}). 아니면 null
 */
public record ArgViolation(String field, Reason reason, String message, Double min, Double max) {

    public static ArgViolation of(String field, Reason reason, String message) {
        return new ArgViolation(field, reason, message, null, null);
    }

    /** 응답 {@code errors[]} 항목. code는 위반 종류(예: {@code MODEL_CONSTRAINT}) */
    public FieldErrorDetail toFieldError() {
        return new FieldErrorDetail(field, reason.name(), message);
    }

    /**
     * 위반 종류와 제어 창구 검사 단계(BR-ACT-01: 기능 스키마 → 모델 제약 → 조직 절대 한계).
     * 앞 단계에서 걸리면 뒤 단계는 보지 않는다.
     */
    public enum Reason {
        /** 카탈로그에 없는 기능 */
        CAPABILITY_NOT_SUPPORTED("CAPABILITY_NOT_SUPPORTED", 0),
        /** 기능에 없는 명령(상태만 있는 기능 포함) */
        COMMAND_NOT_SUPPORTED("CAPABILITY_NOT_SUPPORTED", 0),
        /** 명령이 설정하지 않는 인자(읽기 전용 속성 포함) */
        UNKNOWN_ARG("COMMAND_ARGS_INVALID", 1),
        /** 필수 인자 없음 */
        MISSING_ARG("COMMAND_ARGS_INVALID", 1),
        /** 인자 개수 부족(minProperties) */
        TOO_FEW_ARGS("COMMAND_ARGS_INVALID", 1),
        /** 타입 불일치 */
        WRONG_TYPE("COMMAND_ARGS_INVALID", 1),
        /** 기능 정의의 허용 값 밖 */
        NOT_ALLOWED_VALUE("COMMAND_ARGS_INVALID", 1),
        /** 기능 정의의 기본 범위 밖 */
        OUT_OF_STANDARD_RANGE("COMMAND_ARGS_INVALID", 1),
        /** 값 간격(step) 불일치 */
        STEP_MISMATCH("COMMAND_ARGS_INVALID", 1),
        /** 기기 모델 제약 밖(ACT-01.03) */
        MODEL_CONSTRAINT("COMMAND_ARG_OUT_OF_RANGE", 2),
        /** 조직 절대 한계 밖(ACT-06.04, BR-ACT-09) */
        ABSOLUTE_LIMIT("COMMAND_ABSOLUTE_LIMIT", 3);

        private final String resultCode;
        private final int stage;

        Reason(String resultCode, int stage) {
            this.resultCode = resultCode;
            this.stage = stage;
        }

        /** API 오류 코드(ACT domain-model §5) */
        public String resultCode() {
            return resultCode;
        }

        /** 검사 단계(작을수록 먼저) */
        public int stage() {
            return stage;
        }
    }
}
