package net.java21.data2flow.contracts.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.java21.data2flow.contracts.error.ErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * 필터처럼 {@code @RestControllerAdvice}가 닿지 않는 곳에서 공통 실패 형식을 쓴다(api-rules §4).
 * 문구는 요청의 Accept-Language로 현지화한다.
 */
public class ServletErrorWriter {

    private final ErrorMessages messages;
    private final JsonMapper jsonMapper;

    public ServletErrorWriter(ErrorMessages messages, JsonMapper jsonMapper) {
        this.messages = messages;
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code,
                      Map<String, String> headers, Object... args) throws IOException {
        // Accept-Language가 없으면 서버 기본 언어가 아니라 원문 언어(ko)로 답한다(ADR-037)
        Locale locale = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE) == null ? ErrorMessages.DEFAULT_LOCALE : request.getLocale();
        String message = messages.resolve(code, locale, args);
        response.setStatus(code.httpStatus());
        headers.forEach(response::setHeader);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(ErrorResponse.of(code.code(), message)));
    }
}
