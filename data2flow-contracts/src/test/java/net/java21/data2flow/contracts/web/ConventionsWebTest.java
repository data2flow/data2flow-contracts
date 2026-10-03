package net.java21.data2flow.contracts.web;

import net.java21.data2flow.contracts.identity.DataflowHeaders;
import net.java21.data2flow.contracts.ratelimit.RateLimitInfo;
import net.java21.data2flow.contracts.error.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** OPS-12.04·OPS-12.05: 서블릿 서비스에서 낙관적 잠금 충돌 → 409, 한도 초과 예외 → 429 + 헤더 */
@WebMvcTest(controllers = ConventionsWebTest.ConventionController.class)
@Import(ConventionsWebTest.ConventionController.class)
class ConventionsWebTest {

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("[OPS-12.04][TC-OPS-127] JPA @Version 충돌(OptimisticLockingFailureException) → 409 VERSION_CONFLICT, If-Match는 무시")
    void optimisticLock() throws Exception {
        mvc.perform(patch("/core/things/1").header("If-Match", "\"3\"")
                        .header(DataflowHeaders.USER_ID, "7").header(DataflowHeaders.ORG_ID, "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.header.resultCode").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.header.resultMessage").value("다른 사용자가 먼저 수정했습니다. 새로고침 후 다시 시도해 주세요"));
    }

    @Test
    @DisplayName("[OPS-12.05][AT-OPS-25.6] 한도 초과 예외 → 429 RATE_LIMITED, Retry-After·X-RateLimit-* 헤더")
    void rateLimited() throws Exception {
        mvc.perform(get("/core/limited").header(DataflowHeaders.USER_ID, "7").header(DataflowHeaders.ORG_ID, "1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(header().string("X-RateLimit-Limit", "60"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().string("X-RateLimit-Reset", "30"))
                .andExpect(header().exists(DataflowHeaders.REQUEST_ID))
                .andExpect(jsonPath("$.header.resultCode").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.header.resultMessage").value("요청이 너무 많습니다. 30초 후 다시 시도해 주세요"));
    }

    @SpringBootApplication
    static class TestApp {
    }

    @RestController
    static class ConventionController {

        @PatchMapping("/core/things/{thing-id}")
        ApiResponse<String> patchThing() {
            throw new OptimisticLockingFailureException("stale");
        }

        @GetMapping("/core/limited")
        ApiResponse<String> limited() {
            throw new RateLimitInfo(60, 0, 30).exceeded(CommonErrorCode.RATE_LIMITED);
        }
    }
}
