package net.java21.data2flow.contracts.identity;

/** gateway가 넣은 신원. 서비스는 이 값을 믿는다(내부망 신뢰, ADR-021). ID는 문자열이 아니라 내부에서는 long으로 다룬다. */
public record CurrentUser(long userId, long organizationId) {

    public static CurrentUser fromHeaders(String userId, String organizationId) {
        if (userId == null || organizationId == null) {
            throw new IllegalArgumentException("신원 헤더가 없습니다");
        }
        return new CurrentUser(Long.parseLong(userId.trim()), Long.parseLong(organizationId.trim()));
    }
}
