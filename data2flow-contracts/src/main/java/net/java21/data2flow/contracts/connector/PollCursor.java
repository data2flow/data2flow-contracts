package net.java21.data2flow.contracts.connector;

/**
 * 폴링 위치(DSC domain-model §6.5 {@code source_poll_cursors}, DSC-09.09). 마지막 커서·시각·ETag와 진행 중 페이지 토큰.
 * 확인 방식이 {@link AckMode#CURSOR}인 커넥터는 받은 것을 {@link RawSink}에 기록하고 그 단계가 정상 완료된 뒤에만 저장한다
 * (BR-DSC-24). 그래서 재시작해도 이어서 읽고 같은 데이터를 두 번 저장하지 않는다.
 *
 * @param cursor    마지막 커서(증분 기준: 시각 ISO-8601·커서 문자열·ETag 등, 1,024자 이하)
 * @param pageToken 진행 중 페이지 토큰. 페이지가 끝났으면 null
 */
public record PollCursor(String cursor, String pageToken) {

    /** 열 길이(varchar(1024)) */
    public static final int MAX_LENGTH = 1024;

    public PollCursor {
        if (cursor != null && cursor.length() > MAX_LENGTH || pageToken != null && pageToken.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("cursor·pageToken은 " + MAX_LENGTH + "자 이하입니다");
        }
    }

    /** 처음(아무것도 읽지 않음) */
    public static PollCursor initial() {
        return new PollCursor(null, null);
    }

    /** 페이지를 다 읽고 다음 증분 기준으로 넘어간 위치 */
    public static PollCursor at(String cursor) {
        return new PollCursor(cursor, null);
    }

    /** 같은 증분 기준 안에서 다음 페이지로 */
    public PollCursor nextPage(String token) {
        return new PollCursor(cursor, token);
    }

    public boolean isInitial() {
        return cursor == null && pageToken == null;
    }
}
