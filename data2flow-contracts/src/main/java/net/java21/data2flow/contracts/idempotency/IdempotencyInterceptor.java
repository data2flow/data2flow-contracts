package net.java21.data2flow.contracts.idempotency;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.java21.data2flow.contracts.error.BusinessException;
import net.java21.data2flow.contracts.error.CommonErrorCode;
import net.java21.data2flow.contracts.error.FieldErrorDetail;
import net.java21.data2flow.contracts.identity.CurrentUser;
import net.java21.data2flow.contracts.identity.CurrentUserHolder;
import net.java21.data2flow.contracts.identity.DataflowHeaders;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * {@link Idempotent} 엔드포인트에서 멱등 키를 판정한다(OPS-12.03, BR-OPS-20). 본문은 {@link IdempotencyFilter}가 미리 읽어 둔다.
 * 범위는 (조직, 사용자, 메서드 + 경로 템플릿)이고, 요청 해시는 메서드·실제 경로·쿼리·본문으로 만든다. 그래서 같은 키를
 * 다른 기기 경로에 다시 쓰면 같은 템플릿이어도 409 IDEMPOTENCY_KEY_REUSED다.
 */
public class IdempotencyInterceptor implements HandlerInterceptor {

    static final String CLAIMED_SCOPE = IdempotencyInterceptor.class.getName() + ".scope";
    /** 키 형식: UUID 권장, 최대 64자(BR-OPS-20) */
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    private final IdempotencyStore store;
    private final IdempotencyProperties properties;
    private final Clock clock;

    public IdempotencyInterceptor(IdempotencyStore store, IdempotencyProperties properties, Clock clock) {
        this.store = store;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        Idempotent idempotent = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), Idempotent.class);
        if (idempotent == null) {
            idempotent = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), Idempotent.class);
        }
        if (idempotent == null) {
            return true;
        }
        String key = request.getHeader(DataflowHeaders.IDEMPOTENCY_KEY);
        if (key == null || key.isBlank()) {
            if (idempotent.required()) {
                throw invalidKey("NotBlank");
            }
            return true;
        }
        if (!KEY.matcher(key).matches()) {
            throw invalidKey("Pattern");
        }
        CachedBodyRequest cached = WebUtils.getNativeRequest(request, CachedBodyRequest.class);
        if (cached == null) {
            throw new IllegalStateException("IdempotencyFilter가 등록되지 않았습니다");
        }
        IdempotencyScope scope = scope(request, key);
        Instant now = clock.instant();
        IdempotencyClaim claim = store.claim(scope, hash(request, cached.body()), now,
                now.plus(properties.ttl()), now.minus(properties.inProgressTimeout()));
        switch (claim.outcome()) {
            case ACQUIRED -> {
                request.setAttribute(CLAIMED_SCOPE, scope);
                return true;
            }
            case REPLAY -> {
                replay(response, claim.response());
                return false;
            }
            case IN_PROGRESS -> throw new BusinessException(CommonErrorCode.IDEMPOTENCY_CONFLICT)
                    .withHeader(DataflowHeaders.RETRY_AFTER, Long.toString(Math.max(1, properties.retryAfter().toSeconds())));
            default -> throw new BusinessException(CommonErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
    }

    private static IdempotencyScope scope(HttpServletRequest request, String key) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = request.getMethod() + " " + (pattern != null ? pattern : request.getRequestURI());
        if (route.length() > 200) {
            route = route.substring(0, 200);
        }
        CurrentUser user = CurrentUserHolder.find().orElse(null);
        return user == null
                ? new IdempotencyScope(0, 0, route, key)
                : new IdempotencyScope(user.organizationId(), user.userId(), route, key);
    }

    static String hash(HttpServletRequest request, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String head = request.getMethod() + "\n" + request.getRequestURI() + "?"
                    + (request.getQueryString() == null ? "" : request.getQueryString()) + "\n";
            digest.update(head.getBytes(StandardCharsets.UTF_8));
            digest.update(body);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void replay(HttpServletResponse response, StoredResponse stored) throws IOException {
        response.setStatus(stored.status());
        stored.headers().forEach(response::setHeader);
        if (stored.contentType() != null) {
            response.setContentType(stored.contentType());
        }
        response.getOutputStream().write(stored.body());
    }

    private static BusinessException invalidKey(String code) {
        return new BusinessException(CommonErrorCode.INVALID_REQUEST,
                List.of(new FieldErrorDetail(DataflowHeaders.IDEMPOTENCY_KEY, code, null)));
    }
}
