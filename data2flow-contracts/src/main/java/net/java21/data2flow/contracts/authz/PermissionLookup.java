package net.java21.data2flow.contracts.authz;

/**
 * 사용자의 현재 권한을 원천에서 찾는 SPI(design/auth.md §7). 각 서블릿 서비스가 빈 하나로 제공한다.
 *
 * <ul>
 *   <li>core-api: {@code data2flow_core.user_roles}·{@code custom_roles}를 읽고 공간 트리를 펼친다(원천).</li>
 *   <li>그 밖의 서비스: core 내부 API로 묻는다. 호출 비용을 줄이려면 {@link CachingPermissionLookup}(10초 이내)으로 감싼다.</li>
 * </ul>
 * 사용자가 없거나 ACTIVE가 아니거나 조직이 다르면 {@link AccessGrant#none()}을 돌려준다(기본 거부).
 * 원천에 닿지 못하면 예외를 던진다(fail-closed, 503은 서비스 예외 처리에서).
 *
 * <p>장기 토큰(API 키·MCP, {@code X-ACCESS-TOKEN-ID}) 요청의 권한은 웹 신원과 다르다(소유자 권한 ∩ 토큰 범위·공간, 서비스 계정은
 * 범위 권한만, IAM-05·IAM-04.07). {@link RoleChecker}는 {@link #find(long, long, Long)}로 토큰 ID를 넘긴다. 토큰을 아는 원천은
 * {@link #tokenAware(TokenAware)}로 만들고(core 내부 API {@code access-grant?accessTokenId=}), 토큰을 모르는 이전 구현(2인자 람다)은
 * 토큰 ID를 무시하고 사용자 권한을 돌려준다(이전 호환, 범위 권한은 RoleChecker가 {@code X-TOKEN-SCOPE}로 한 번 더 거른다).
 */
@FunctionalInterface
public interface PermissionLookup {

    /** 웹 신원(토큰 없음)의 권한 */
    AccessGrant find(long organizationId, long userId);

    /**
     * 신원의 권한. {@code accessTokenId}가 null이면 웹 신원이고, 있으면 그 장기 토큰 주체의 권한이다.
     * 기본 구현은 토큰을 모르는 이전 원천용으로 {@link #find(long, long)}를 부른다.
     */
    default AccessGrant find(long organizationId, long userId, Long accessTokenId) {
        return find(organizationId, userId);
    }

    /** 토큰 ID까지 받는 원천으로 {@link PermissionLookup}을 만든다. 예: {@code PermissionLookup.tokenAware(core::accessGrant)} */
    static PermissionLookup tokenAware(TokenAware source) {
        return new PermissionLookup() {
            @Override
            public AccessGrant find(long organizationId, long userId) {
                return source.find(organizationId, userId, null);
            }

            @Override
            public AccessGrant find(long organizationId, long userId, Long accessTokenId) {
                return source.find(organizationId, userId, accessTokenId);
            }
        };
    }

    /** 웹 신원과 장기 토큰 주체를 함께 판정하는 원천 */
    @FunctionalInterface
    interface TokenAware {

        /** @param accessTokenId 장기 토큰 ID. 웹 신원이면 null */
        AccessGrant find(long organizationId, long userId, Long accessTokenId);
    }
}
