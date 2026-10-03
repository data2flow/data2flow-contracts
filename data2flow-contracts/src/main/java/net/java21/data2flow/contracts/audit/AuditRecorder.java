package net.java21.data2flow.contracts.audit;

/**
 * 감사 기록 창구(IAM-06.01). 저장은 core-api의 {@code data2flow_core.audit_logs}(INSERT 전용) 한 곳이다.
 *
 * <ul>
 *   <li>core-api: DB에 바로 INSERT하는 구현을 둔다.</li>
 *   <li>그 밖의 서비스: core 내부 API {@code POST /internal/core/audit-logs}(API-IAM-39, 202)를 부르는 구현을 둔다.
 *       감사 실패가 업무를 막지 않도록 비동기로 보내되, 제어 명령 감사는 동기로 기록한다.</li>
 * </ul>
 * 이 인터페이스의 구현 빈이 있으면 {@code RoleChecker}가 권한 거부(403)를 {@code ACCESS_DENIED}로 남긴다.
 */
@FunctionalInterface
public interface AuditRecorder {

    void record(AuditEvent event);
}
