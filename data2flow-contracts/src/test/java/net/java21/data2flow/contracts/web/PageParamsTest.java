package net.java21.data2flow.contracts.web;

import net.java21.data2flow.contracts.error.BusinessException;
import net.java21.data2flow.contracts.error.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** OPS-12.02·BR-OPS-19: 목록 파라미터 공통 규칙(page 1부터, size 기본 20·최대 100, 커서 기본 50·최대 500, sort, keyword) */
class PageParamsTest {

    @ParameterizedTest(name = "page={0}, size={1} → {2}, {3}")
    @CsvSource(nullValues = "null", value = {
            "null, null, 1, 20",
            "0, 0, 1, 20",
            "-3, 500, 1, 100",
            "3, 50, 3, 50"})
    @DisplayName("[OPS-12.02] 범위 밖 page·size는 오류 없이 경계값으로 보정")
    void clamp(Integer page, Integer size, int expectedPage, int expectedSize) {
        PageParams params = PageParams.of(page, size);
        assertThat(params.page()).isEqualTo(expectedPage);
        assertThat(params.size()).isEqualTo(expectedSize);
        assertThat(params.offset()).isEqualTo((long) (expectedPage - 1) * expectedSize);
    }

    @Test
    @DisplayName("[OPS-12.02][AT-OPS-25.2] 항목 131개, page=0&size=1000 → page=1, size=100, totalPages=2, responses 100개")
    void boundaryCorrection() {
        // given
        List<Integer> all = IntStream.range(0, 131).boxed().toList();
        // when
        PageParams params = PageParams.of(0, 1000);
        List<Integer> page = all.subList((int) params.offset(), (int) Math.min(all.size(), params.offset() + params.size()));
        ListApiResponse<Integer> response = ListApiResponse.of(params, page, all.size());
        // then
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(100);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.totalCount()).isEqualTo(131);
        assertThat(response.responses()).hasSize(100);
        assertThat(ListApiResponse.of(params, Collections.<Integer>emptyList(), 0).responses()).isEmpty();
    }

    @Test
    @DisplayName("[OPS-12.02][AT-OPS-25.7] 커서 목록은 size 기본 50·최대 500, 한 건 더 읽어 다음 커서를 판단한다")
    void cursorParams() {
        assertThat(CursorParams.of(null, null)).isEqualTo(new CursorParams(null, 50));
        assertThat(CursorParams.of(" ", 1000).size()).isEqualTo(500);
        assertThat(CursorParams.of("c2", 0).size()).isEqualTo(50);
        assertThat(CursorParams.of("c2", 500).fetchSize()).isEqualTo(501);
        assertThat(CursorParams.of("c2", 10).cursor()).isEqualTo("c2");
    }

    @Test
    @DisplayName("[OPS-12.02] sort=필드,asc|desc 반복, 허용 필드만 받아 ORDER BY로 바꾼다")
    void sort() {
        Set<String> allowed = Set.of("name", "createdAt");
        SortParams sort = SortParams.parse(List.of("name,desc", "createdAt"), allowed);
        assertThat(sort.orders()).containsExactly(new SortParams.Order("name", false), new SortParams.Order("createdAt", true));
        assertThat(sort.toOrderBy(Map.of("name", "name", "createdAt", "created_at"))).isEqualTo("name DESC, created_at ASC");
        assertThat(SortParams.parse(null, allowed, new SortParams.Order("createdAt", false)).orders())
                .containsExactly(new SortParams.Order("createdAt", false));
        assertThat(SortParams.parse(List.of(" "), allowed).toOrderBy(Map.of())).isEmpty();
        assertThatThrownBy(() -> sort.toOrderBy(Map.of("name", "name"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[OPS-12.02] 허용 밖 정렬 필드·방향은 400 INVALID_REQUEST, errors[0].field=sort")
    void sortRejected() {
        for (String bad : List.of("password,asc", "name,up", "name,asc,x", "name; DROP TABLE")) {
            assertThatThrownBy(() -> SortParams.parse(List.of(bad), Set.of("name")))
                    .isInstanceOfSatisfying(BusinessException.class, e -> {
                        assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST);
                        assertThat(e.getErrors().getFirst().field()).isEqualTo("sort");
                    });
        }
    }

    @Test
    @DisplayName("[OPS-12.02] keyword는 앞뒤 공백을 지우고 비면 조건 없음")
    void keyword() {
        assertThat(PageParams.keyword("  EM300 ")).isEqualTo("EM300");
        assertThat(PageParams.keyword("   ")).isNull();
        assertThat(PageParams.keyword(null)).isNull();
    }
}
