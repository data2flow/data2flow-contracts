package net.java21.data2flow.contracts.authz;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static net.java21.data2flow.contracts.authz.Permission.*;
import static org.assertj.core.api.Assertions.assertThat;

/** IAM-04.07·BR-IAM-19: 장기 토큰 범위가 여는 권한 */
class ApiScopeTest {

    @Test
    @DisplayName("[IAM-04.07] 범위 이름 6개가 스펙과 같고 쓰기·제어 범위만 승인이 필요하다")
    void codes() {
        assertThat(Arrays.stream(ApiScope.values()).map(ApiScope::code)).containsExactly(
                "read:telemetry", "read:devices", "read:analytics", "write:devices", "control:devices", "mcp:write");
        assertThat(Arrays.stream(ApiScope.values()).filter(ApiScope::requiresApproval))
                .containsExactly(ApiScope.WRITE_DEVICES, ApiScope.CONTROL_DEVICES, ApiScope.MCP_WRITE);
        assertThat(ApiScope.fromCode("nope")).isEmpty();
    }

    @Test
    @DisplayName("[IAM-04.07][BR-IAM-19] 어떤 범위로도 회원·토큰 관리, 감사, 스크립트·제어 플로우 배포, 승인을 할 수 없다")
    void forbiddenForTokens() {
        List<String> all = Arrays.stream(ApiScope.values()).map(ApiScope::code).toList();
        for (Permission p : List.of(IAM_MANAGE, API_TOKEN_ISSUE, AUDIT_READ, SCRIPT_WRITE, FLOW_DEPLOY_CONTROL, FLOW_APPROVE)) {
            assertThat(ApiScope.allows(all, p)).as(p.name()).isFalse();
        }
        assertThat(ApiScope.allows(List.of("read:devices"), DEV_READ)).isTrue();
        assertThat(ApiScope.allows(List.of("read:devices"), DEVICE_CONTROL)).isFalse();
        assertThat(ApiScope.allows(List.of("control:devices", "unknown"), DEVICE_CONTROL)).isTrue();
    }
}
