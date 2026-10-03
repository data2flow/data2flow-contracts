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
 */
@FunctionalInterface
public interface PermissionLookup {

    AccessGrant find(long organizationId, long userId);
}
