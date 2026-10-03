package net.java21.data2flow.contracts.message.event;

import java.util.List;

/**
 * EVT-DEV-07 {@code group.membership.changed}: 기기 그룹 구성원이 바뀌었다(생산 core-api).
 *
 * @param groupId 그룹 ID
 * @param added   추가된 기기 ID
 * @param removed 빠진 기기 ID
 */
public record GroupMembershipChanged(long groupId, List<Long> added, List<Long> removed) implements EventPayload {

    public GroupMembershipChanged {
        added = added == null ? List.of() : List.copyOf(added);
        removed = removed == null ? List.of() : List.copyOf(removed);
    }
}
