package net.java21.data2flow.contracts.audit;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 자동 제어 원인(IAM-06.04, audit_logs.cause). 감사 상세에서 원인 플로우 실행 기록으로 이동할 때 쓴다.
 *
 * @param flowId           원인 플로우 ID
 * @param flowVersion      적용된 플로우 버전
 * @param nodeId           명령을 낸 노드 ID
 * @param triggerMessageId 실행을 일으킨 메시지의 messageId
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditCause(String flowId, Integer flowVersion, String nodeId, String triggerMessageId) {
}
