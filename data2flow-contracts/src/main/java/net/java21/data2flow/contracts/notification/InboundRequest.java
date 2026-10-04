package net.java21.data2flow.contracts.notification;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 메신저 콜백 원본(API-RUL-31: 외부 → BFF → {@code POST /internal/action/notifications/callbacks/{channel}}, 원본 그대로).
 * 헤더 이름은 대소문자를 가리지 않는다.
 *
 * @param headers 요청 헤더
 * @param body    요청 본문 바이트
 */
public record InboundRequest(Map<String, String> headers, byte[] body) {

    public InboundRequest {
        TreeMap<String, String> h = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null) {
            h.putAll(headers);
        }
        headers = java.util.Collections.unmodifiableMap(h);
        body = body == null ? new byte[0] : body.clone();
    }

    /** 헤더 값. 없으면 null */
    public String header(String name) {
        return headers.get(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    public String bodyAsString() {
        return new String(body, StandardCharsets.UTF_8);
    }
}
