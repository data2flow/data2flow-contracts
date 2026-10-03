package net.java21.data2flow.contracts.identity;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * gateway가 넣은 신원. 서비스는 이 값을 믿는다(내부망 신뢰, ADR-021). ID는 내부에서 long으로 다룬다.
 *
 * <p>장기 토큰(MCP)으로 온 요청에는 토큰 ID와 범위(scope)가 함께 온다(design/auth.md §5, §8).
 * 웹 요청에서는 {@code accessTokenId}가 null이고 {@code scopes}는 비어 있다.
 *
 * @param userId         X-USER-ID
 * @param organizationId X-ORG-ID
 * @param accessTokenId  X-ACCESS-TOKEN-ID, 장기 토큰 요청에만 있다
 * @param scopes         X-TOKEN-SCOPE(공백·쉼표 구분), 장기 토큰 요청에만 있다(IAM-04.07)
 */
public record CurrentUser(long userId, long organizationId, Long accessTokenId, Set<String> scopes) {

    public CurrentUser {
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }

    public CurrentUser(long userId, long organizationId) {
        this(userId, organizationId, null, Set.of());
    }

    public static CurrentUser fromHeaders(String userId, String organizationId) {
        return fromHeaders(userId, organizationId, null, null);
    }

    /**
     * gateway 헤더에서 신원을 만든다. 값이 없거나 숫자가 아니면 {@link IllegalArgumentException}.
     */
    public static CurrentUser fromHeaders(String userId, String organizationId, String accessTokenId, String scopes) {
        if (isBlank(userId) || isBlank(organizationId)) {
            throw new IllegalArgumentException("신원 헤더가 없습니다");
        }
        Long tokenId = isBlank(accessTokenId) ? null : Long.parseLong(accessTokenId.trim());
        return new CurrentUser(Long.parseLong(userId.trim()), Long.parseLong(organizationId.trim()), tokenId, parseScopes(scopes));
    }

    /** 장기 토큰(API 키·MCP)으로 온 요청인가 */
    public boolean viaAccessToken() {
        return accessTokenId != null;
    }

    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }

    static Set<String> parseScopes(String raw) {
        if (isBlank(raw)) {
            return Set.of();
        }
        return Arrays.stream(raw.split("[\\s,]+")).filter(s -> !s.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
