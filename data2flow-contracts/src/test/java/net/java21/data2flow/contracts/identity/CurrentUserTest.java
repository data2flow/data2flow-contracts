package net.java21.data2flow.contracts.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserTest {

    @Test
    void parsesGatewayHeaders() {
        assertThat(CurrentUser.fromHeaders(" 7", "1")).isEqualTo(new CurrentUser(7, 1));
        assertThatThrownBy(() -> CurrentUser.fromHeaders(null, "1")).isInstanceOf(IllegalArgumentException.class);
        assertThat(DataflowHeaders.IDENTITY_HEADERS).contains("X-USER-ID", "X-ORG-ID");
    }
}
