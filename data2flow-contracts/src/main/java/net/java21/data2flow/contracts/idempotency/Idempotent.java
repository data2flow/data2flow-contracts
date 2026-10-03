package net.java21.data2flow.contracts.idempotency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 엔드포인트는 {@code Idempotency-Key}를 받는다(OPS-12.03, BR-OPS-20). 컨트롤러 메서드(또는 클래스)에 붙여 켠다.
 *
 * <ul>
 *   <li>같은 키·같은 요청(메서드·경로·쿼리·본문)으로 24시간 안에 다시 오면 처음 응답(상태·본문·Location)을 그대로 돌려주고
 *       컨트롤러를 다시 실행하지 않는다(AT-OPS-25.3).</li>
 *   <li>같은 키·다른 요청이면 409 {@code IDEMPOTENCY_KEY_REUSED}(AT-OPS-25.4).</li>
 *   <li>첫 요청이 아직 처리 중이면 409 {@code IDEMPOTENCY_CONFLICT} + {@code Retry-After}.</li>
 * </ul>
 * 키의 범위는 (조직, 사용자, 메서드 + 경로 템플릿)이다. 처음 응답이 4xx·5xx이면 저장하지 않고 키를 풀어 같은 키로 다시 시도할 수 있다.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Idempotent {

    /** true면 헤더가 없을 때 400 INVALID_REQUEST(예: 기기 수동 제어 API-ACT, "Idempotency-Key 필수") */
    boolean required() default false;
}
