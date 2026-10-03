package net.java21.data2flow.contracts.capability;

import net.java21.data2flow.contracts.error.FieldErrorDetail;

import java.util.List;
import java.util.Optional;

/**
 * 명령 검증 결과. 위반은 모두 같은 검사 단계의 것이다(앞 단계에서 걸리면 뒤 단계는 보지 않는다).
 *
 * @param violations 위반 목록. 비어 있으면 통과
 */
public record CommandValidation(List<ArgViolation> violations) {

    public static final CommandValidation OK = new CommandValidation(List.of());

    public CommandValidation {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public boolean ok() {
        return violations.isEmpty();
    }

    /** 실패면 API 오류 코드(예: COMMAND_ARG_OUT_OF_RANGE). 통과면 빈 값 */
    public Optional<String> resultCode() {
        return violations.stream().findFirst().map(v -> v.reason().resultCode());
    }

    /** 실패면 첫 위반의 종류 */
    public Optional<ArgViolation.Reason> reason() {
        return violations.stream().findFirst().map(ArgViolation::reason);
    }

    /** 응답 {@code errors[]} */
    public List<FieldErrorDetail> fieldErrors() {
        return violations.stream().map(ArgViolation::toFieldError).toList();
    }
}
