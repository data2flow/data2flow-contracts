package net.java21.data2flow.contracts.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PageParamsTest {

    @ParameterizedTest(name = "page={0}, size={1} → {2}, {3}")
    @CsvSource(nullValues = "null", value = {
            "null, null, 1, 20",
            "0, 0, 1, 20",
            "-3, 500, 1, 100",
            "3, 50, 3, 50"})
    @DisplayName("OPS-12.02 범위 밖 page·size는 오류 없이 경계값으로 보정")
    void clamp(Integer page, Integer size, int expectedPage, int expectedSize) {
        PageParams params = PageParams.of(page, size);
        assertThat(params.page()).isEqualTo(expectedPage);
        assertThat(params.size()).isEqualTo(expectedSize);
        assertThat(params.offset()).isEqualTo((long) (expectedPage - 1) * expectedSize);
    }
}
