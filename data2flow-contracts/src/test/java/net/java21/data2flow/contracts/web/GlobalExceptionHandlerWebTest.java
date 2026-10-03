package net.java21.data2flow.contracts.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import net.java21.data2flow.contracts.error.BusinessException;
import net.java21.data2flow.contracts.error.CommonErrorCode;
import net.java21.data2flow.contracts.identity.DataflowHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TC-OPS-145(AT-OPS-25.8): 오류 응답 형식과 Accept-Language 현지화. resultCode는 언어와 관계없이 같다 */
@WebMvcTest(controllers = GlobalExceptionHandlerWebTest.SampleController.class)
@Import(GlobalExceptionHandlerWebTest.SampleController.class)
@TestPropertySource(properties = "data2flow.identity.enabled=false")
class GlobalExceptionHandlerWebTest {

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("TC-OPS-145 AT-OPS-25.8 빈 이름 + Accept-Language en → 400 INVALID_REQUEST, errors[0].field=name, 영어 문구")
    void validationInEnglish() throws Exception {
        mvc.perform(post("/samples").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}").header("Accept-Language", "en"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.header.isSuccessful").value(false))
                .andExpect(jsonPath("$.header.resultCode").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.header.resultMessage").value("Please check your input"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].code").value("NotBlank"));
    }

    @Test
    @DisplayName("TC-OPS-145 같은 오류를 ko·ja·zh로 요청하면 문구만 바뀐다. 지원하지 않는 언어는 한국어")
    void languages() throws Exception {
        String[][] cases = {{"ko", "입력값을 확인해 주세요"}, {"ja", "入力内容を確認してください"}, {"zh-CN", "请检查输入内容"}, {"fr", "입력값을 확인해 주세요"}};
        for (String[] c : cases) {
            mvc.perform(post("/samples").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}").header("Accept-Language", c[0]))
                    .andExpect(jsonPath("$.header.resultCode").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.header.resultMessage").value(c[1]));
        }
    }

    @Test
    @DisplayName("업무 예외는 코드의 HTTP 상태와 자리표시자 문구로 답한다")
    void businessException() throws Exception {
        mvc.perform(get("/samples/limited").header("Accept-Language", "ko"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.header.resultCode").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.header.resultMessage").value("요청이 너무 많습니다. 30초 후 다시 시도해 주세요"));
    }

    @Test
    @DisplayName("본문을 읽을 수 없으면 400, 없는 경로는 404 RESOURCE_NOT_FOUND")
    void badBodyAndNotFound() throws Exception {
        mvc.perform(post("/samples").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.header.resultCode").value("INVALID_REQUEST"));
        mvc.perform(get("/nowhere"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.header.resultCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("예상하지 못한 오류는 500 INTERNAL_ERROR, 문구에 요청 ID가 들어가고 응답 헤더로도 돌려준다")
    void unknownError() throws Exception {
        mvc.perform(get("/samples/boom").header(DataflowHeaders.REQUEST_ID, "req-123"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(DataflowHeaders.REQUEST_ID, "req-123"))
                .andExpect(jsonPath("$.header.resultCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.header.resultMessage").value(org.hamcrest.Matchers.containsString("req-123")));
    }

    @Test
    @DisplayName("형식이 이상한 X-REQUEST-ID는 버리고 새 ID를 만든다")
    void unsafeRequestId() throws Exception {
        mvc.perform(get("/samples/ok").header(DataflowHeaders.REQUEST_ID, "bad id\nINJECT"))
                .andExpect(status().isOk())
                .andExpect(header().string(DataflowHeaders.REQUEST_ID, matchesPattern("[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.response").value("ok"));
    }

    @SpringBootApplication
    static class TestApp {
    }

    record SampleRequest(@NotBlank String name) {
    }

    @RestController
    static class SampleController {

        @PostMapping("/samples")
        ApiResponse<String> create(@Valid @RequestBody SampleRequest request) {
            return ApiResponse.success(request.name());
        }

        @GetMapping("/samples/limited")
        ApiResponse<String> limited() {
            throw new BusinessException(CommonErrorCode.RATE_LIMITED, 30);
        }

        @GetMapping("/samples/boom")
        ApiResponse<String> boom() {
            throw new IllegalStateException("boom");
        }

        @GetMapping("/samples/ok")
        ApiResponse<String> ok() {
            return ApiResponse.success("ok");
        }
    }
}
