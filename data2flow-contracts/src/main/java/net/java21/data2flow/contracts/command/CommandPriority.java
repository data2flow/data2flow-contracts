package net.java21.data2flow.contracts.command;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * 명령 우선순위(ACT domain-model commands.priority, BR-ACT-24): MANUAL &gt; SAFETY &gt; SCHEDULE &gt; AUTO = AI.
 * <b>출처가 정하고 요청자가 고를 수 없다.</b> 요청에 priority가 있어도 {@link #forSource}로 다시 정한다.
 */
public enum CommandPriority {
    MANUAL(4),
    SAFETY(3),
    SCHEDULE(2),
    AUTO(1),
    AI(1),
    /** 이 코드보다 새 생산자가 보낸 값. 가장 낮게 본다 */
    @JsonEnumDefaultValue
    UNKNOWN(0);

    private final int rank;

    CommandPriority(int rank) {
        this.rank = rank;
    }

    /** 클수록 높다. AUTO와 AI는 같다 */
    public int rank() {
        return rank;
    }

    /** {@code other}보다 높은지(같으면 false) */
    public boolean outranks(CommandPriority other) {
        return rank > other.rank;
    }

    /**
     * 자동 우선순위인지: 비상 정지에 막히고(BR-ACT-12) 최소 간격을 적용받는다(BR-ACT-07). MANUAL·SAFETY는 아니다.
     */
    public boolean automatic() {
        return this == SCHEDULE || this == AUTO || this == AI;
    }

    /**
     * 출처의 우선순위(BR-ACT-24). 장면({@link SourceType#SCENE})은 실행 출처를 따르므로 {@link #forScene}을 쓴다.
     *
     * @throws IllegalArgumentException SCENE·UNKNOWN
     */
    public static CommandPriority forSource(SourceType type) {
        return switch (type) {
            case USER, BULK -> MANUAL;
            case SYSTEM -> SAFETY;
            case SCHEDULE -> SCHEDULE;
            case FLOW, RULE -> AUTO;
            case AI -> AI;
            case SCENE, UNKNOWN -> throw new IllegalArgumentException(type + "는 출처만으로 우선순위를 정할 수 없습니다");
        };
    }

    /** 장면 실행 명령의 우선순위: 장면을 실행한 출처(화면 = MANUAL, 예약 = SCHEDULE, 플로우 = AUTO, AI = AI)를 따른다 */
    public static CommandPriority forScene(SourceType runOrigin) {
        if (runOrigin == SourceType.SCENE) {
            throw new IllegalArgumentException("장면 실행 출처는 장면일 수 없습니다");
        }
        return forSource(runOrigin);
    }
}
