package net.java21.data2flow.contracts.secret;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** NFR-03.02: 비밀값은 화면·API 응답·로그에 드러나지 않는다 */
class SecretTest {

    record SourceResponse(String name, Secret password) {
    }

    record SourceRequest(String name, Secret password) {
    }

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    @DisplayName("[NFR-03.02][AT-NFR-08.2] 응답 JSON·toString에는 ***만 나가고, 요청 본문의 평문은 받아들인다(쓰기 전용)")
    void neverSerialized() {
        Secret secret = Secret.of("broker-pass-1");
        assertThat(json.writeValueAsString(new SourceResponse("mqtt", secret)))
                .isEqualTo("{\"name\":\"mqtt\",\"password\":\"***\"}");
        assertThat(secret.toString()).isEqualTo("***");
        assertThat(String.valueOf(new SourceResponse("mqtt", secret))).doesNotContain("broker-pass-1");

        SourceRequest request = json.readValue("{\"name\":\"mqtt\",\"password\":\"broker-pass-1\"}", SourceRequest.class);
        assertThat(request.password().reveal()).isEqualTo("broker-pass-1");
    }

    @Test
    @DisplayName("비교는 값으로, 해시는 값을 드러내지 않는다")
    void equality() {
        assertThat(Secret.of("a")).isEqualTo(Secret.of("a")).isNotEqualTo(Secret.of("b")).isNotEqualTo("a");
        assertThat(Secret.of("a").hashCode()).isEqualTo(Secret.of("b").hashCode());
        assertThat(Secret.of("").isEmpty()).isTrue();
        assertThatThrownBy(() -> Secret.of(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
