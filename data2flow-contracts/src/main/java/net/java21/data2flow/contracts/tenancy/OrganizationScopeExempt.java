package net.java21.data2flow.contracts.tenancy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 조직 조건 없이 조회해도 되는 리포지토리 메서드·클래스에 붙인다(BR-IAM-01 예외, design/testing/backend.md §6).
 * {@code data2flow-contracts-test}의 ArchUnit 규칙 {@code Data2flowArchRules.REPOSITORY_QUERIES_ARE_ORGANIZATION_SCOPED}가
 * 이 어노테이션이 붙은 곳은 건너뛴다. 예: 조직 테이블 자체, 기한 지난 행 정리 배치, 토큰 해시로 찾는 인증 조회.
 * 이유를 꼭 적는다(리뷰에서 확인).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface OrganizationScopeExempt {

    /** 조직 조건이 필요 없는 이유 */
    String value();
}
