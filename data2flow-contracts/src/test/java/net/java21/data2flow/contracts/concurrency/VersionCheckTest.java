package net.java21.data2flow.contracts.concurrency;

import net.java21.data2flow.contracts.error.BusinessException;
import net.java21.data2flow.contracts.error.CommonErrorCode;
import net.java21.data2flow.contracts.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** OPS-12.04·BR-OPS-21: baseVersion 낙관적 잠금 */
class VersionCheckTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    @DisplayName("[OPS-12.04][AT-OPS-25.5] version 3 객체를 baseVersion=2로 수정 → 409 VERSION_CONFLICT")
    void conflict() {
        assertThatThrownBy(() -> VersionCheck.require(2, 3))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.VERSION_CONFLICT));
        assertThatCode(() -> VersionCheck.require(3, 3)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("[OPS-12.04] 도메인 전용 코드가 있으면 그것을 쓴다(예: FLOW_VERSION_CONFLICT)")
    void domainCode() {
        ErrorCode flowConflict = new ErrorCode() {
            public String code() {
                return "FLOW_VERSION_CONFLICT";
            }

            public int httpStatus() {
                return 409;
            }
        };
        assertThatThrownBy(() -> VersionCheck.require(1, 2, flowConflict))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(flowConflict));
        assertThatThrownBy(() -> VersionCheck.requireUpdated(0, flowConflict)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("[OPS-12.04] 조건부 UPDATE가 0행이면 409, 1행이면 통과")
    void conditionalUpdate() {
        assertThatThrownBy(() -> VersionCheck.requireUpdated(0))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.VERSION_CONFLICT));
        assertThatCode(() -> VersionCheck.requireUpdated(1)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("[OPS-12.04] PATCH 본문의 baseVersion: 없거나 정수가 아니면 400 INVALID_REQUEST(errors[0].field=baseVersion)")
    void patchBody() {
        assertThat(VersionCheck.baseVersion(json.readTree("{\"name\":\"a\",\"baseVersion\":3}"))).isEqualTo(3);
        for (String body : new String[]{"{\"name\":\"a\"}", "{\"baseVersion\":null}", "{\"baseVersion\":\"3\"}", "{\"baseVersion\":-1}"}) {
            assertThatThrownBy(() -> VersionCheck.baseVersion(json.readTree(body)))
                    .isInstanceOfSatisfying(BusinessException.class, e -> {
                        assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST);
                        assertThat(e.getErrors().getFirst().field()).isEqualTo("baseVersion");
                    });
        }
        assertThatThrownBy(() -> VersionCheck.baseVersion(null)).isInstanceOf(BusinessException.class);
    }
}
