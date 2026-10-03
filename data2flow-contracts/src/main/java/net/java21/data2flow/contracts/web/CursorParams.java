package net.java21.data2flow.contracts.web;

/**
 * 커서 목록 파라미터 {@code cursor}·{@code size}(기본 50, 최대 500, 범위 밖은 경계값). 쌓이기만 하는 큰 목록 6종에만 쓴다
 * (원본 메시지, 감사 로그, 명령 이력, 알림 발송 이력, 디버그·추적 이벤트, 시계열 원본 점; api-rules §3.4, BR-OPS-19, AT-OPS-25.7).
 * 응답은 {@link CursorListApiResponse}다.
 *
 * @param cursor 이전 응답의 {@code nextCursor}. 처음이면 null
 * @param size   한 번에 가져올 개수
 */
public record CursorParams(String cursor, int size) {

    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 500;

    public static CursorParams of(String cursor, Integer size) {
        int s = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return new CursorParams(cursor == null || cursor.isBlank() ? null : cursor, s);
    }

    /** 다음 페이지가 있는지 알려고 한 건 더 읽는 SQL LIMIT 값 */
    public int fetchSize() {
        return size + 1;
    }
}
