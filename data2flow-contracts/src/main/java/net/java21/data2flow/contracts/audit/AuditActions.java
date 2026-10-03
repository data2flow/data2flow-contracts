package net.java21.data2flow.contracts.audit;

/**
 * 여러 서비스가 함께 쓰는 감사 action 코드. 도메인별 코드(예: IAM의 {@code USER_LOGGED_IN})는
 * 각 도메인 {@code spec/detail/{CODE}/domain-model.md}가 정본이고 서비스가 상수로 둔다.
 */
public final class AuditActions {

    /** 권한 거부(403). detail에 {@code permission}을 남긴다(BR-IAM-17, AT-IAM-11.3) */
    public static final String ACCESS_DENIED = "ACCESS_DENIED";

    private AuditActions() {
    }
}
