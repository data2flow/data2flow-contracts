package net.java21.data2flow.contracts.audit;

/**
 * 감사 행위자 종류(IAM-06.01, domain-model §2.8, DDL {@code ck_audit_logs_actor_type}).
 * SERVICE는 사용자를 대신하지 않은 서비스 자체의 행위(예: 스케줄러 정리, {@code svc:data2flow-flow-engine})다.
 */
public enum AuditActorType {
    USER,
    SERVICE_ACCOUNT,
    FLOW,
    SERVICE,
    SYSTEM
}
