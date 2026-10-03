package net.java21.data2flow.contracts.authz;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 한 사용자가 지금 가진 권한(역할 권한 + 공간 범위). 요청마다 원천 데이터에서 판정한다(BR-IAM-13).
 *
 * @param role        기본 역할 이름(ADMIN …) 또는 {@code CUSTOM}. 표시·감사용이고 판정은 {@code permissions}로 한다
 * @param permissions 역할이 가진 권한
 * @param spaceScope  공간 범위
 */
public record AccessGrant(String role, Set<Permission> permissions, SpaceScope spaceScope) {

    public static final String CUSTOM_ROLE = "CUSTOM";
    private static final AccessGrant NONE = new AccessGrant("NONE", Set.of(), SpaceScope.only(Set.of()));

    public AccessGrant {
        permissions = permissions == null || permissions.isEmpty()
                ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(permissions));
        spaceScope = spaceScope == null ? SpaceScope.all() : spaceScope;
    }

    /** 기본 역할의 권한 */
    public static AccessGrant of(BuiltinRole role, SpaceScope spaceScope) {
        return new AccessGrant(role.name(), role.permissions(), spaceScope);
    }

    /** 사용자 정의 역할의 권한(IAM-04.03) */
    public static AccessGrant custom(Set<Permission> permissions, SpaceScope spaceScope) {
        return new AccessGrant(CUSTOM_ROLE, permissions, spaceScope);
    }

    /** 권한 없음(없는 사용자, 비활성 사용자, 다른 조직). 기본 거부 */
    public static AccessGrant none() {
        return NONE;
    }

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }
}
