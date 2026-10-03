package net.java21.data2flow.contracts.authz;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static net.java21.data2flow.contracts.authz.Permission.*;
import static org.assertj.core.api.Assertions.assertThat;

/** IAM-04.01·IAM-04.04: 기본 역할 5개와 Permission 목록이 spec/IAM-identity.md 권한표와 같다 */
class PermissionCatalogTest {

    /**
     * 권한 → 기본 역할(A·I·O·AN·V) 표. spec/IAM-identity.md "Permission 목록"의 "기본 역할" 열을 그대로 옮겼다.
     * 행이 빠지거나 역할이 하나라도 다르면 실패한다(권한 매트릭스, AT-IAM-11.1).
     */
    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {
            "IAM_MANAGE|A", "AUDIT_READ|A", "API_TOKEN_ISSUE|A I O AN",
            "SRC_READ|A I O", "SRC_ADMIN|A I", "SCRIPT_READ|A I O", "SCRIPT_WRITE|A I",
            "INGEST_READ|A I O", "INGEST_PAYLOAD_READ|A I", "INGEST_REPROCESS|A I",
            "DEV_READ|A I O AN V", "DEV_PLACE|A I O", "DEV_ADMIN|A I", "WORKORDER_WRITE|A I O",
            "DEVICE_CONTROL|A I O", "SCENE_RUN|A I O", "SCENE_MANAGE|A I", "SCHEDULE_MANAGE|A I",
            "CONTROL_SETTINGS|A I", "INTERLOCK_MANAGE|A I", "DRIVER_MANAGE|A I", "CAPABILITY_MANAGE|A I",
            "EMERGENCY_STOP|A I O", "EMERGENCY_RELEASE|A I",
            "FLOW_READ|A I O AN", "FLOW_WRITE|A I O", "FLOW_DEPLOY_CONTROL|A I", "FLOW_APPROVE|A",
            "SINK_CONNECTION_MANAGE|A I", "GIT_SYNC_MANAGE|A I", "NODE_PACKAGE_MANAGE|A",
            "RULE_READ|A I O AN", "RULE_WRITE|A I O", "ALARM_READ|A I O AN V", "ALARM_HANDLE|A I O",
            "NOTIFY_POLICY_WRITE|A I O", "NOTIFY_CHANNEL_MANAGE|A",
            "ANALYTICS_READ|A I O AN V", "ANALYTICS_RUN|A I O AN", "AI_USE|A I O AN V",
            "SIM_READ|A I O AN", "SIM_RUN|A I O AN", "SIM_MANAGE|A I", "SIM_ADMIN|A",
            "DASHBOARD_READ|A I O AN V", "DASHBOARD_WRITE|A I O AN", "BRANDING_MANAGE|A", "OPS_MANAGE|A",
            "TS_READ|A I O AN V", "TS_EXPORT|A I O AN", "TS_IMPORT|A I", "TS_POLICY|A I",
            "CMP_READ|A I O AN V", "CMP_REPORT|A I O AN", "CMP_ACTION|A I O", "CMP_ADMIN|A I",
            "OCC_READ|A I O AN", "OCC_MANAGE|A I O", "BOOKING_READ|A I O AN", "BOOKING_ADMIN|A I",
            "ENE_READ|A I O AN V", "ENE_OPERATE|A I O", "ENE_ANALYZE|A I O AN", "ENE_ADMIN|A I"})
    @DisplayName("[IAM-04.01][AT-IAM-11.1] 권한별 기본 역할이 권한표와 같다")
    void matrix(Permission permission, String roles) {
        Set<BuiltinRole> expected = Arrays.stream(roles.split(" ")).map(PermissionCatalogTest::role).collect(Collectors.toSet());
        Set<BuiltinRole> actual = Arrays.stream(BuiltinRole.values()).filter(r -> r.has(permission)).collect(Collectors.toSet());
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("[IAM-04.01] 권한표 행 수와 enum 값 수가 같다(표에 없는 권한을 만들지 않음)")
    void everyPermissionIsInMatrix() {
        assertThat(Permission.values()).hasSize(64);
        assertThat(BuiltinRole.values()).extracting(Enum::name)
                .containsExactly("ADMIN", "INTEGRATOR", "OPERATOR", "ANALYST", "VIEWER");
    }

    @Test
    @DisplayName("[IAM-04.04][TC-IAM-140] 기기 수동 제어와 제어 노드 플로우 적용은 별도 권한이다. ANALYST·VIEWER는 둘 다 없고 OPERATOR는 제어만 있다")
    void controlPermissionsAreSeparate() {
        assertThat(DEVICE_CONTROL).isNotEqualTo(FLOW_DEPLOY_CONTROL);
        assertThat(BuiltinRole.OPERATOR.has(DEVICE_CONTROL)).isTrue();
        assertThat(BuiltinRole.OPERATOR.has(FLOW_DEPLOY_CONTROL)).isFalse();
        assertThat(BuiltinRole.OPERATOR.has(FLOW_WRITE)).isTrue();
        for (BuiltinRole role : EnumSet.of(BuiltinRole.ANALYST, BuiltinRole.VIEWER)) {
            assertThat(role.has(DEVICE_CONTROL)).as(role.name()).isFalse();
            assertThat(role.has(FLOW_DEPLOY_CONTROL)).as(role.name()).isFalse();
        }
    }

    @Test
    @DisplayName("[IAM-04.03][BR-IAM-30] IAM_MANAGE·AUDIT_READ만 사용자 정의 역할에 넣을 수 없다. 영역은 스펙 도메인 코드")
    void customRoleRestrictions() {
        assertThat(Arrays.stream(Permission.values()).filter(p -> !p.customRoleAllowed()))
                .containsExactlyInAnyOrder(IAM_MANAGE, AUDIT_READ);
        assertThat(DEVICE_CONTROL.area()).isEqualTo("ACT");
        assertThat(FLOW_DEPLOY_CONTROL.area()).isEqualTo("FLW");
    }

    private static BuiltinRole role(String code) {
        return switch (code) {
            case "A" -> BuiltinRole.ADMIN;
            case "I" -> BuiltinRole.INTEGRATOR;
            case "O" -> BuiltinRole.OPERATOR;
            case "AN" -> BuiltinRole.ANALYST;
            case "V" -> BuiltinRole.VIEWER;
            default -> throw new IllegalArgumentException(code);
        };
    }
}
