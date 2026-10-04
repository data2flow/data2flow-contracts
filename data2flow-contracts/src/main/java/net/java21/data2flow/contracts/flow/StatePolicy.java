package net.java21.data2flow.contracts.flow;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * 노드 상태 이어받기 정책(FLW-06.03, BR-FLW-07, flow-engine-and-live-reload.md §4.3). 노드 설정이 바뀐 채 새 버전이 적용될 때 노드
 * 종류의 {@code statePolicy(old, new)}가 고른다. 노드가 그대로면 상태를 그대로 쓰고, 새 노드는 빈 상태, 삭제된 노드의 상태는 24시간
 * 보관(롤백 때 복원)한다. 적용 전 검증(API-FLW-06)과 편집기는 노드별 정책을 이 값으로 보여 준다.
 */
public enum StatePolicy {
    /** 그대로 유지(예: 임계값만 바뀜 → 진행 중인 지속 타이머 유지) */
    KEEP,
    /** 버리고 새로 시작(예: 측정 항목이 바뀜) */
    RESET,
    /** 새 설정에 맞게 바꿔 유지(예: 집계 창 길이 변경 → 버퍼를 새 창으로 자름) */
    MIGRATE,
    /** 이 코드보다 새 엔진이 보낸 값. 안전하게 RESET으로 다룬다 */
    @JsonEnumDefaultValue
    UNKNOWN;

    /** 정책을 알 수 없을 때의 안전한 처리 */
    public StatePolicy effective() {
        return this == UNKNOWN ? RESET : this;
    }

    /** 여러 정책 중 가장 보수적인 것(RESET > MIGRATE > KEEP). 한 노드의 여러 설정이 바뀌었을 때 합친다 */
    public static StatePolicy strictest(StatePolicy a, StatePolicy b) {
        StatePolicy x = a == null ? KEEP : a.effective();
        StatePolicy y = b == null ? KEEP : b.effective();
        return rank(x) >= rank(y) ? x : y;
    }

    private static int rank(StatePolicy p) {
        return switch (p) {
            case KEEP -> 0;
            case MIGRATE -> 1;
            case RESET, UNKNOWN -> 2;
        };
    }
}
