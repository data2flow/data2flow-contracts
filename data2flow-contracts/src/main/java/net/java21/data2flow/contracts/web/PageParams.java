package net.java21.data2flow.contracts.web;

/**
 * 오프셋 페이지 파라미터. page는 1부터, size는 기본 20·최대 100이고 범위 밖 값은 오류 없이 경계값으로 보정한다
 * (design/api-rules.md §3.4, OPS-12.02, BR-OPS-19, AT-OPS-25.2). 정렬은 {@link SortParams}, 검색어는 {@link #keyword(String)}.
 */
public record PageParams(int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public static PageParams of(Integer page, Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return new PageParams(p, s);
    }

    /** SQL OFFSET 값 */
    public long offset() {
        return (long) (page - 1) * size;
    }

    /** 검색어 {@code keyword}: 앞뒤 공백을 지우고, 비어 있으면 null(조건 없음) */
    public static String keyword(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
