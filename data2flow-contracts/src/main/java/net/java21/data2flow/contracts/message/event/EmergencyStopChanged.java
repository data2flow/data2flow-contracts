package net.java21.data2flow.contracts.message.event;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonInclude;
import net.java21.data2flow.contracts.command.CommandPriority;

import java.time.Instant;
import java.util.List;

/**
 * EVT-ACT-03 {@code control.emergency.started}·{@code control.emergency.released}: 자동화 비상 정지(ACT-06.03, 생산 core-api →
 * 소비 모든 화면 SSE 배너, flow-engine, action). 범위 안에서는 AUTO·SCHEDULE·AI 명령을 SKIPPED(EMERGENCY_STOP)로 막고
 * MANUAL·SAFETY는 허용한다(BR-ACT-12, {@link Scope#blocks}).
 *
 * @param id     비상 정지 ID(emergency_stops.id)
 * @param scope  범위
 * @param reason 사유
 * @param by     실행(해제) 사용자
 * @param at     시작(해제) 시각
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmergencyStopChanged(long id, Scope scope, String reason, Long by, Instant at) implements EventPayload {

    public EmergencyStopChanged {
        if (id < 1 || scope == null || at == null) {
            throw new IllegalArgumentException("id·scope·at은 필수입니다");
        }
    }

    /**
     * 범위: 조직 전체 또는 공간(하위 포함).
     *
     * @param type            ORG 또는 SPACE
     * @param spaceId         SPACE 범위의 공간
     * @param includeChildren 하위 공간 포함(기본 true)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Scope(Type type, Long spaceId, Boolean includeChildren) {

        public Scope {
            if (type == null || (type == Type.SPACE && spaceId == null)) {
                throw new IllegalArgumentException("SPACE 범위에는 spaceId가 필요합니다");
            }
        }

        public static Scope organization() {
            return new Scope(Type.ORG, null, null);
        }

        public static Scope space(long spaceId) {
            return new Scope(Type.SPACE, spaceId, true);
        }

        /** 이 우선순위의 명령을 막는가(BR-ACT-12) */
        public static boolean blocks(CommandPriority priority) {
            return priority == CommandPriority.AUTO || priority == CommandPriority.SCHEDULE || priority == CommandPriority.AI;
        }

        /**
         * 이 범위가 공간을 덮는가.
         *
         * @param spacePathIds 대상 공간의 루트부터 자기까지 ID(예: {@code /1/7/31} → [1, 7, 31]). 공간이 없으면 빈 목록
         */
        public boolean covers(List<Long> spacePathIds) {
            if (type == Type.ORG) {
                return true;
            }
            if (type != Type.SPACE || spacePathIds == null || spacePathIds.isEmpty()) {
                return false;
            }
            if (Boolean.FALSE.equals(includeChildren)) {
                return spaceId.equals(spacePathIds.get(spacePathIds.size() - 1));
            }
            return spacePathIds.contains(spaceId);
        }

        public enum Type {
            ORG, SPACE,
            @JsonEnumDefaultValue
            UNKNOWN
        }
    }
}
