package net.java21.data2flow.contracts.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.java21.data2flow.contracts.error.CommonErrorCode;
import net.java21.data2flow.contracts.identity.DataflowHeaders;
import net.java21.data2flow.contracts.web.ServletErrorWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * {@code Idempotency-Key}가 붙은 쓰기 요청의 본문을 미리 읽고 응답을 잡아 둔다(OPS-12.03). 실제 판정은
 * {@link IdempotencyInterceptor}가 {@link Idempotent} 엔드포인트에서만 한다. 요청이 끝나면 2xx·3xx 응답은 저장하고,
 * 4xx·5xx·예외면 키를 푼다.
 */
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
    private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final IdempotencyStore store;
    private final ServletErrorWriter errorWriter;
    private final int maxBodyBytes;

    public IdempotencyFilter(IdempotencyStore store, ServletErrorWriter errorWriter, int maxBodyBytes) {
        this.store = store;
        this.errorWriter = errorWriter;
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getHeader(DataflowHeaders.IDEMPOTENCY_KEY) == null || !MUTATING.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CachedBodyRequest cached;
        try {
            cached = CachedBodyRequest.read(request, maxBodyBytes);
        } catch (CachedBodyRequest.PayloadTooLargeException ex) {
            errorWriter.write(request, response, CommonErrorCode.PAYLOAD_TOO_LARGE, Map.of(), maxBodyBytes + " bytes");
            return;
        }
        ContentCachingResponseWrapper captured = new ContentCachingResponseWrapper(response);
        boolean completed = false;
        try {
            chain.doFilter(cached, captured);
            completed = true;
        } finally {
            settle(cached, captured, completed);
            captured.copyBodyToResponse();
        }
    }

    private void settle(HttpServletRequest request, ContentCachingResponseWrapper response, boolean completed) {
        IdempotencyScope scope = (IdempotencyScope) request.getAttribute(IdempotencyInterceptor.CLAIMED_SCOPE);
        if (scope == null) {
            return;
        }
        try {
            if (completed && response.getStatus() < 400) {
                store.complete(scope, capture(response));
            } else {
                store.release(scope);
            }
        } catch (RuntimeException ex) {
            // 업무 처리는 이미 끝났다. 저장 실패는 같은 키 재요청이 inProgressTimeout 뒤 다시 실행될 수 있다는 뜻이므로 남긴다
            log.error("멱등 키 결과 저장 실패: route={}", scope.route(), ex);
        }
    }

    private static StoredResponse capture(ContentCachingResponseWrapper response) {
        Map<String, String> headers = new HashMap<>();
        String location = response.getHeader(HttpHeaders.LOCATION);
        if (location != null) {
            headers.put(HttpHeaders.LOCATION, location);
        }
        return new StoredResponse(response.getStatus(), response.getContentType(), headers, response.getContentAsByteArray());
    }
}
