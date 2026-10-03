package net.java21.data2flow.contracts.web;

import java.util.List;

/**
 * 오프셋 목록 응답 {@code {header, page, size, totalPages, responses, totalCount}} (design/api-rules.md §3).
 * page는 1부터 센다.
 */
public record ListApiResponse<T>(ApiHeader header, int page, int size, int totalPages, List<T> responses, long totalCount) {

    public static <T> ListApiResponse<T> of(PageParams params, List<T> responses, long totalCount) {
        int totalPages = totalCount == 0 ? 0 : (int) ((totalCount + params.size() - 1) / params.size());
        return new ListApiResponse<>(ApiHeader.success(), params.page(), params.size(), totalPages, List.copyOf(responses), totalCount);
    }
}
