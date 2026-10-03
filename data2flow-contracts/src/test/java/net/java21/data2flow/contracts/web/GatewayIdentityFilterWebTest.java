package net.java21.data2flow.contracts.web;

import net.java21.data2flow.contracts.identity.CurrentUser;
import net.java21.data2flow.contracts.identity.CurrentUserHolder;
import net.java21.data2flow.contracts.identity.DataflowHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** IAM-07.09·design/auth.md §7: gateway 신원 헤더를 CurrentUserHolder에 담고, 없거나 틀리면 401 AUTH_TOKEN_INVALID */
@WebMvcTest(controllers = GatewayIdentityFilterWebTest.WhoAmIController.class)
@Import(GatewayIdentityFilterWebTest.WhoAmIController.class)
@TestPropertySource(properties = "data2flow.identity.optional-paths=/core/public/**")
class GatewayIdentityFilterWebTest {

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("[IAM-07.09][AT-IAM-11.4] 신원 헤더가 없으면 401 AUTH_TOKEN_INVALID와 WWW-Authenticate, 문구는 Accept-Language")
    void missingIdentity() throws Exception {
        mvc.perform(get("/core/me").header("Accept-Language", "en"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""))
                .andExpect(jsonPath("$.header.isSuccessful").value(false))
                .andExpect(jsonPath("$.header.resultCode").value("AUTH_TOKEN_INVALID"))
                .andExpect(jsonPath("$.header.resultMessage").value("Please sign in again"));
        mvc.perform(get("/core/me").header(DataflowHeaders.USER_ID, "7"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[IAM-07.09] 숫자가 아닌 신원 헤더는 선택 경로에서도 401")
    void malformedIdentity() throws Exception {
        mvc.perform(get("/core/me").header(DataflowHeaders.USER_ID, "1 OR 1=1").header(DataflowHeaders.ORG_ID, "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.header.resultCode").value("AUTH_TOKEN_INVALID"));
        mvc.perform(get("/internal/core/ping").header(DataflowHeaders.USER_ID, "x").header(DataflowHeaders.ORG_ID, "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[IAM-07.09] 헤더의 사용자·조직·토큰 범위가 요청 동안 CurrentUserHolder에 있다")
    void identityInHolder() throws Exception {
        mvc.perform(get("/core/me").header(DataflowHeaders.USER_ID, "7").header(DataflowHeaders.ORG_ID, "3")
                        .header(DataflowHeaders.ACCESS_TOKEN_ID, "55").header(DataflowHeaders.TOKEN_SCOPE, "read:devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.userId").value("7"))
                .andExpect(jsonPath("$.response.organizationId").value("3"))
                .andExpect(jsonPath("$.response.viaToken").value(true));
    }

    @Test
    @DisplayName("내부 API·설정한 공개 경로는 신원 없이 통과하고, 헤더가 있으면 담는다")
    void optionalPaths() throws Exception {
        mvc.perform(get("/internal/core/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.userId").value("-"));
        mvc.perform(get("/core/public/ping"))
                .andExpect(status().isOk());
        mvc.perform(get("/internal/core/ping").header(DataflowHeaders.USER_ID, "9").header(DataflowHeaders.ORG_ID, "1"))
                .andExpect(jsonPath("$.response.userId").value("9"));
        mvc.perform(options("/core/me"))
                .andExpect(status().isOk());
    }

    @SpringBootApplication
    static class TestApp {
    }

    @RestController
    static class WhoAmIController {

        @GetMapping("/core/me")
        ApiResponse<Map<String, Object>> me() {
            CurrentUser user = CurrentUserHolder.get();
            return ApiResponse.success(Map.of("userId", Long.toString(user.userId()),
                    "organizationId", Long.toString(user.organizationId()), "viaToken", user.viaAccessToken()));
        }

        @GetMapping({"/internal/core/ping", "/core/public/ping"})
        ApiResponse<Map<String, Object>> ping() {
            return ApiResponse.success(Map.of("userId",
                    CurrentUserHolder.find().map(u -> Long.toString(u.userId())).orElse("-")));
        }
    }
}
