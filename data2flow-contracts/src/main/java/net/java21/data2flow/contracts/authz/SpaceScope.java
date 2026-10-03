package net.java21.data2flow.contracts.authz;

import java.util.Set;

/**
 * 사용자 권한이 미치는 공간 범위(IAM-04.02, user_roles.space_scope). 전체 공간이거나, 허용된 공간 ID 집합이다.
 *
 * <p>{@code allowedSpaceIds}는 <b>하위 공간까지 펼친</b> 집합이다. 공간 트리는 core-api만 알므로 펼치는 일은
 * {@link PermissionLookup} 구현(core는 재귀 쿼리, 다른 서비스는 core 내부 API 결과)이 한다.
 * 조회 리포지토리는 제한된 범위일 때 {@code space_id IN (:allowedSpaceIds)} 조건을 붙인다(IAM-04.06, BR-IAM-16).
 */
public record SpaceScope(boolean unrestricted, Set<Long> allowedSpaceIds) {

    private static final SpaceScope ALL = new SpaceScope(true, Set.of());

    public SpaceScope {
        allowedSpaceIds = unrestricted || allowedSpaceIds == null ? Set.of() : Set.copyOf(allowedSpaceIds);
    }

    public static SpaceScope all() {
        return ALL;
    }

    /** 펼친 공간 ID 집합으로 제한한다. 빈 집합이면 어느 공간도 볼 수 없다 */
    public static SpaceScope only(Set<Long> expandedSpaceIds) {
        return new SpaceScope(false, expandedSpaceIds);
    }

    /** 이 공간이 범위 안인가. 공간에 속하지 않은 조직 단위 자원(spaceId null)은 범위와 관계없이 보인다 */
    public boolean includes(Long spaceId) {
        return unrestricted || spaceId == null || allowedSpaceIds.contains(spaceId);
    }
}
