package net.java21.data2flow.contracts.authz;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static net.java21.data2flow.contracts.authz.Permission.*;

/**
 * 장기 토큰(API 키·MCP)의 범위(IAM-04.07, IAM-05.02)와 범위가 여는 권한(design/api/AIA-api.md §3 MCP 도구표).
 *
 * <p>토큰 요청은 (사용자 역할의 권한) ∩ (토큰 범위가 여는 권한) 안에서만 허용된다. 어떤 범위도 회원·토큰 관리, 감사 조회,
 * 스크립트 배포, 제어 노드 플로우 적용, 플로우 승인을 열지 않는다(BR-IAM-19). {@link #MCP_WRITE}가 여는 FLOW_WRITE·RULE_WRITE는
 * 초안 생성용이므로, 플로우·규칙을 적용(배포)하는 API는 {@code RoleChecker.requireInteractive()}로 토큰 요청을 따로 막는다.
 */
public enum ApiScope {
    READ_TELEMETRY("read:telemetry", EnumSet.of(TS_READ, ALARM_READ)),
    READ_DEVICES("read:devices", EnumSet.of(DEV_READ, FLOW_READ)),
    READ_ANALYTICS("read:analytics", EnumSet.of(ANALYTICS_READ, ENE_READ)),
    WRITE_DEVICES("write:devices", EnumSet.of(DEV_PLACE)),
    CONTROL_DEVICES("control:devices", EnumSet.of(DEVICE_CONTROL)),
    MCP_WRITE("mcp:write", EnumSet.of(ANALYTICS_RUN, ALARM_HANDLE, RULE_WRITE, FLOW_WRITE));

    private final String code;
    private final Set<Permission> permissions;

    ApiScope(String code, Set<Permission> permissions) {
        this.code = code;
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    /** 토큰·헤더에 쓰는 이름(예: {@code read:telemetry}) */
    public String code() {
        return code;
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    /** 쓰기·제어 범위는 ADMIN 승인 뒤에만 활성화된다(BR-IAM-19) */
    public boolean requiresApproval() {
        return this == WRITE_DEVICES || this == CONTROL_DEVICES || this == MCP_WRITE;
    }

    public static Optional<ApiScope> fromCode(String code) {
        for (ApiScope scope : values()) {
            if (scope.code.equals(code)) {
                return Optional.of(scope);
            }
        }
        return Optional.empty();
    }

    /** 범위 이름 목록이 이 권한을 여는가. 모르는 범위 이름은 무시한다(기본 거부) */
    public static boolean allows(Collection<String> scopeCodes, Permission permission) {
        return scopeCodes.stream().map(ApiScope::fromCode).flatMap(Optional::stream)
                .anyMatch(scope -> scope.permissions.contains(permission));
    }
}
