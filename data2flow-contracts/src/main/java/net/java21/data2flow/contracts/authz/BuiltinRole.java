package net.java21.data2flow.contracts.authz;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static net.java21.data2flow.contracts.authz.Permission.*;

/**
 * 기본 역할 5개와 각 역할이 갖는 권한(spec/IAM-identity.md "기본 역할 권한표"·"Permission 목록", IAM-04.01).
 * 사용자 정의 역할(IAM-04.03, user_roles.role=CUSTOM)은 이 enum이 아니라 권한 집합으로 표현한다({@link AccessGrant}).
 *
 * <p>"O는 ADMIN 지정 시"인 권한(SCENE_MANAGE, SCHEDULE_MANAGE)은 기본 역할에 넣지 않는다. ADMIN이 사용자 정의 역할로 준다.
 */
public enum BuiltinRole {

    ADMIN(EnumSet.allOf(Permission.class)),

    INTEGRATOR(EnumSet.of(
            API_TOKEN_ISSUE,
            SRC_READ, SRC_ADMIN, SCRIPT_READ, SCRIPT_WRITE,
            INGEST_READ, INGEST_PAYLOAD_READ, INGEST_REPROCESS,
            DEV_READ, DEV_PLACE, DEV_ADMIN, WORKORDER_WRITE,
            DEVICE_CONTROL, SCENE_RUN, SCENE_MANAGE, SCHEDULE_MANAGE, CONTROL_SETTINGS, INTERLOCK_MANAGE,
            DRIVER_MANAGE, CAPABILITY_MANAGE, EMERGENCY_STOP, EMERGENCY_RELEASE,
            FLOW_READ, FLOW_WRITE, FLOW_DEPLOY_CONTROL, SINK_CONNECTION_MANAGE, GIT_SYNC_MANAGE,
            RULE_READ, RULE_WRITE, ALARM_READ, ALARM_HANDLE, NOTIFY_POLICY_WRITE,
            ANALYTICS_READ, ANALYTICS_RUN, AI_USE,
            SIM_READ, SIM_RUN, SIM_MANAGE,
            DASHBOARD_READ, DASHBOARD_WRITE,
            TS_READ, TS_EXPORT, TS_IMPORT, TS_POLICY,
            CMP_READ, CMP_REPORT, CMP_ACTION, CMP_ADMIN,
            OCC_READ, OCC_MANAGE, BOOKING_READ, BOOKING_ADMIN,
            ENE_READ, ENE_OPERATE, ENE_ANALYZE, ENE_ADMIN)),

    OPERATOR(EnumSet.of(
            API_TOKEN_ISSUE,
            SRC_READ, SCRIPT_READ, INGEST_READ,
            DEV_READ, DEV_PLACE, WORKORDER_WRITE,
            DEVICE_CONTROL, SCENE_RUN, EMERGENCY_STOP,
            FLOW_READ, FLOW_WRITE,
            RULE_READ, RULE_WRITE, ALARM_READ, ALARM_HANDLE, NOTIFY_POLICY_WRITE,
            ANALYTICS_READ, ANALYTICS_RUN, AI_USE,
            SIM_READ, SIM_RUN,
            DASHBOARD_READ, DASHBOARD_WRITE,
            TS_READ, TS_EXPORT,
            CMP_READ, CMP_REPORT, CMP_ACTION,
            OCC_READ, OCC_MANAGE, BOOKING_READ,
            ENE_READ, ENE_OPERATE, ENE_ANALYZE)),

    ANALYST(EnumSet.of(
            API_TOKEN_ISSUE,
            DEV_READ,
            FLOW_READ,
            RULE_READ, ALARM_READ,
            ANALYTICS_READ, ANALYTICS_RUN, AI_USE,
            SIM_READ, SIM_RUN,
            DASHBOARD_READ, DASHBOARD_WRITE,
            TS_READ, TS_EXPORT,
            CMP_READ, CMP_REPORT,
            OCC_READ, BOOKING_READ,
            ENE_READ, ENE_ANALYZE)),

    VIEWER(EnumSet.of(
            DEV_READ, ALARM_READ, ANALYTICS_READ, AI_USE, DASHBOARD_READ, TS_READ, CMP_READ, ENE_READ));

    private final Set<Permission> permissions;

    BuiltinRole(Set<Permission> permissions) {
        this.permissions = Collections.unmodifiableSet(EnumSet.copyOf(permissions));
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }
}
