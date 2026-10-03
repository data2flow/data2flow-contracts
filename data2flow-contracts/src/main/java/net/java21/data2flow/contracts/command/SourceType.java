package net.java21.data2flow.contracts.command;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/** 명령 출처 종류(ACT-02.03, BR-ACT-15). 출처가 우선순위를 정한다({@link CommandPriority#forSource}) */
public enum SourceType {
    /** 화면·API의 사용자 명령 */
    USER,
    /** 플로우 제어 노드 */
    FLOW,
    /** 규칙(M4) */
    RULE,
    /** 사람이 승인한 AI 제안 */
    AI,
    /** 예약 */
    SCHEDULE,
    /** 장면 실행(우선순위는 실행 출처를 따름) */
    SCENE,
    /** 일괄 제어(사용자) */
    BULK,
    /** 시스템 안전 동작(인터락 해소 뒤 복귀, 비상 정지 해제 시 기본 상태) */
    SYSTEM,
    /** 이 코드보다 새 생산자가 보낸 종류 */
    @JsonEnumDefaultValue
    UNKNOWN
}
