package net.java21.data2flow.contracts.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.java21.data2flow.contracts.identity.DataflowHeaders;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 요청 ID를 로그(MDC {@code requestId})와 응답 헤더에 싣는다. gateway가 만든 X-REQUEST-ID를 그대로 쓰고,
 * 없으면(내부 호출·배치) 새로 만든다. 형식이 이상한 값은 버리고 새로 만든다(로그 주입 방지).
 */
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "requestId";
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader(DataflowHeaders.REQUEST_ID);
        if (requestId == null || !SAFE.matcher(requestId).matches()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, requestId);
        response.setHeader(DataflowHeaders.REQUEST_ID, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** 현재 요청 ID. 요청 밖에서는 "-" */
    public static String currentRequestId() {
        String id = MDC.get(MDC_KEY);
        return id == null ? "-" : id;
    }
}
