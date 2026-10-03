package net.java21.data2flow.contracts.audit;

/** 감사 결과(DDL {@code ck_audit_logs_result}). DENIED는 권한 거부(403)이다(BR-IAM-17) */
public enum AuditResult {
    SUCCESS,
    FAILURE,
    DENIED
}
