package net.java21.data2flow.contracts.message.event;

/**
 * EVT-DEV-05 {@code space.changed}: 공간이 만들어지거나 이동·수정·삭제되었다(생산 core-api).
 *
 * @param spaceId 공간 ID
 * @param change  바뀐 종류(CREATED·UPDATED·MOVED·DELETED 등)
 * @param path    바뀐 뒤 공간 경로(예: {@code /1/7/31})
 */
public record SpaceChanged(long spaceId, String change, String path) implements EventPayload {
}
