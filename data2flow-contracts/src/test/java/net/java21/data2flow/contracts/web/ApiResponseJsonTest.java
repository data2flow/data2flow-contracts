package net.java21.data2flow.contracts.web;

import tools.jackson.databind.json.JsonMapper;
import net.java21.data2flow.contracts.error.FieldErrorDetail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** OPS-12.01: 응답 형식이 api-rules §3·§4와 같은지 JSON으로 확인한다 */
class ApiResponseJsonTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("OPS-12.01 단건 응답은 {header:{isSuccessful,resultCode,resultMessage}, response}")
    void singleResponse() throws Exception {
        String json = mapper.writeValueAsString(ApiResponse.success(Map.of("id", "17")));
        assertThat(json).isEqualTo("{\"header\":{\"isSuccessful\":true,\"resultCode\":\"SUCCESS\",\"resultMessage\":\"SUCCESS\"},\"response\":{\"id\":\"17\"}}");
    }

    @Test
    @DisplayName("OPS-12.02 목록 응답은 page·size·totalPages·responses·totalCount")
    void listResponse() throws Exception {
        ListApiResponse<String> list = ListApiResponse.of(PageParams.of(2, 20), List.of("a"), 41);
        assertThat(list.totalPages()).isEqualTo(3);
        String json = mapper.writeValueAsString(list);
        assertThat(json).contains("\"page\":2", "\"size\":20", "\"totalPages\":3", "\"responses\":[\"a\"]", "\"totalCount\":41");
        assertThat(ListApiResponse.of(PageParams.of(1, 20), List.of(), 0).totalPages()).isZero();
    }

    @Test
    @DisplayName("커서 목록은 nextCursor가 있고 totalCount가 없다")
    void cursorResponse() throws Exception {
        String json = mapper.writeValueAsString(CursorListApiResponse.of(50, List.of(1), "c2"));
        assertThat(json).contains("\"nextCursor\":\"c2\"").doesNotContain("totalCount");
    }

    @Test
    @DisplayName("오류 응답: errors는 있을 때만 나간다")
    void errorResponse() throws Exception {
        assertThat(mapper.writeValueAsString(ErrorResponse.of("PERMISSION_DENIED", "x"))).doesNotContain("errors");
        String withErrors = mapper.writeValueAsString(ErrorResponse.of("INVALID_REQUEST", "x", List.of(new FieldErrorDetail("name", "NotBlank", "m"))));
        assertThat(withErrors).contains("\"isSuccessful\":false", "\"field\":\"name\"");
    }
}
