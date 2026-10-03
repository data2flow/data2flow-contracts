package net.java21.data2flow.contracts.identity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserTest {

    @Test
    @DisplayName("[IAM-07.09] gateway 헤더에서 신원을 만든다. 없거나 숫자가 아니면 거부")
    void parsesGatewayHeaders() {
        assertThat(CurrentUser.fromHeaders(" 7", "1")).isEqualTo(new CurrentUser(7, 1));
        assertThatThrownBy(() -> CurrentUser.fromHeaders(null, "1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CurrentUser.fromHeaders("7", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CurrentUser.fromHeaders("abc", "1")).isInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("[IAM-04.07] 장기 토큰 요청은 토큰 ID와 범위를 갖는다. 웹 요청은 범위가 비어 있다")
    void accessTokenRequest() {
        CurrentUser web = CurrentUser.fromHeaders("7", "1", null, null);
        assertThat(web.viaAccessToken()).isFalse();
        assertThat(web.scopes()).isEmpty();

        CurrentUser token = CurrentUser.fromHeaders("7", "1", "55", "read:devices, read:telemetry mcp:write");
        assertThat(token.viaAccessToken()).isTrue();
        assertThat(token.accessTokenId()).isEqualTo(55L);
        assertThat(token.scopes()).containsExactlyInAnyOrder("read:devices", "read:telemetry", "mcp:write");
        assertThat(token.hasScope("control:devices")).isFalse();
        assertThat(new CurrentUser(1, 1, null, null).scopes()).isEqualTo(Set.of());
    }

    @Test
    @DisplayName("[IAM-07.09][BR-IAM-37] gateway는 신원·토큰·내부 헤더를 이름과 접두사로 알아본다")
    void gatewayOwnedHeaders() {
        assertThat(DataflowHeaders.isGatewayOwned("x-user-id")).isTrue();
        assertThat(DataflowHeaders.isGatewayOwned("X-ORG-ID")).isTrue();
        assertThat(DataflowHeaders.isGatewayOwned("X-TOKEN-WORKSPACE")).isTrue();
        assertThat(DataflowHeaders.isGatewayOwned("X-Internal-Auth")).isTrue();
        assertThat(DataflowHeaders.isGatewayOwned("X-ACCESS-TOKEN-ID")).isTrue();
        assertThat(DataflowHeaders.isGatewayOwned("x-session-id")).as("현재 세션 ID도 gateway만 넣는다").isTrue();
        assertThat(DataflowHeaders.isGatewayOwned("X-REQUEST-ID")).isFalse();
        assertThat(DataflowHeaders.isGatewayOwned(null)).isFalse();
    }
}
