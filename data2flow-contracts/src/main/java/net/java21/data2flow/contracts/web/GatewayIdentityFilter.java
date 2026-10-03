package net.java21.data2flow.contracts.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.java21.data2flow.contracts.error.CommonErrorCode;
import net.java21.data2flow.contracts.identity.CurrentUser;
import net.java21.data2flow.contracts.identity.CurrentUserHolder;
import net.java21.data2flow.contracts.identity.DataflowHeaders;
import org.springframework.http.server.PathContainer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * gateway가 넣은 신원 헤더(X-USER-ID, X-ORG-ID, 장기 토큰이면 X-ACCESS-TOKEN-ID·X-TOKEN-SCOPE)를 읽어
 * {@link CurrentUserHolder}에 담는다(design/auth.md §7, api-rules §6, crowfoot XUserIdFilter 준용).
 *
 * <ul>
 *   <li>신원이 필요한 경로(기본: 아래 선택 경로 밖 전부)에서 헤더가 없거나 숫자가 아니면 401 {@code AUTH_TOKEN_INVALID}.</li>
 *   <li>신원이 선택인 경로(기본 {@code /internal/**}, {@code /actuator/**}, {@code /error})에서는 헤더가 있을 때만 담는다.
 *       형식이 틀린 헤더는 선택 경로에서도 401이다(위조·버그를 조용히 넘기지 않음).</li>
 * </ul>
 * 서비스는 공개 경로를 {@code data2flow.identity.optional-paths}로 더한다(예: auth의 {@code /auth/login}).
 */
public class GatewayIdentityFilter extends OncePerRequestFilter {

    public static final List<String> DEFAULT_OPTIONAL_PATHS = List.of("/internal/**", "/actuator/**", "/error");

    private final ServletErrorWriter errorWriter;
    private final List<PathPattern> optionalPaths;

    public GatewayIdentityFilter(ServletErrorWriter errorWriter, List<String> optionalPaths) {
        this.errorWriter = errorWriter;
        PathPatternParser parser = PathPatternParser.defaultInstance;
        this.optionalPaths = optionalPaths.stream().map(parser::parse).toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean optional = isOptional(request);
        String userId = request.getHeader(DataflowHeaders.USER_ID);
        String orgId = request.getHeader(DataflowHeaders.ORG_ID);
        CurrentUser user = null;
        if (userId != null || orgId != null || !optional) {
            try {
                user = CurrentUser.fromHeaders(userId, orgId,
                        request.getHeader(DataflowHeaders.ACCESS_TOKEN_ID), request.getHeader(DataflowHeaders.TOKEN_SCOPE));
            } catch (IllegalArgumentException ex) { // NumberFormatException 포함
                errorWriter.write(request, response, CommonErrorCode.AUTH_TOKEN_INVALID,
                        Map.of("WWW-Authenticate", "Bearer error=\"invalid_token\""));
                return;
            }
        }
        if (user == null) {
            chain.doFilter(request, response);
            return;
        }
        CurrentUserHolder.set(user);
        try {
            chain.doFilter(request, response);
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    private boolean isOptional(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        PathContainer container = PathContainer.parsePath(path);
        return optionalPaths.stream().anyMatch(p -> p.matches(container));
    }
}
