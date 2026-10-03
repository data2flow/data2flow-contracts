package net.java21.data2flow.contracts.web;

import java.util.List;

/**
 * 커서 목록 응답 {@code {header, size, responses, nextCursor}}. 쌓이기만 하는 큰 목록 6종에만 쓴다
 * (원본 메시지, 감사 로그, 명령 이력, 알림 발송 이력, 디버그·추적, 시계열 원본 점). totalCount는 없다.
 */
public record CursorListApiResponse<T>(ApiHeader header, int size, List<T> responses, String nextCursor) {

    public static <T> CursorListApiResponse<T> of(int size, List<T> responses, String nextCursor) {
        return new CursorListApiResponse<>(ApiHeader.success(), size, List.copyOf(responses), nextCursor);
    }
}
