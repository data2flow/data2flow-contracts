package net.java21.data2flow.contracts.idempotency;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * 처음 요청의 응답. 재요청에 그대로 돌려준다.
 *
 * @param status      HTTP 상태
 * @param contentType Content-Type(본문이 없으면 null)
 * @param headers     함께 돌려줄 헤더(Location)
 * @param body        본문 바이트(없으면 길이 0)
 */
public record StoredResponse(int status, String contentType, Map<String, String> headers, byte[] body) {

    public StoredResponse {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        body = body == null ? new byte[0] : body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof StoredResponse other && status == other.status && Objects.equals(contentType, other.contentType)
                && headers.equals(other.headers) && Arrays.equals(body, other.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, contentType, headers, Arrays.hashCode(body));
    }

    @Override
    public String toString() {
        return "StoredResponse[status=" + status + ", contentType=" + contentType + ", headers=" + headers
                + ", bodyLength=" + body.length + "]";
    }
}
