package net.java21.data2flow.contracts.authz;

import net.java21.data2flow.contracts.audit.AuditEvent;
import net.java21.data2flow.contracts.audit.AuditResult;
import net.java21.data2flow.contracts.error.BusinessException;
import net.java21.data2flow.contracts.error.CommonErrorCode;
import net.java21.data2flow.contracts.error.ErrorCode;
import net.java21.data2flow.contracts.identity.CurrentUser;
import net.java21.data2flow.contracts.identity.CurrentUserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** IAM-04.01·IAM-04.04·IAM-04.05: RoleChecker는 남의 것은 404, 역할 부족은 403 PERMISSION_DENIED + 감사 ACCESS_DENIED */
class RoleCheckerTest {

    private static final long ORG_A = 1;
    private static final long ORG_B = 2;
    private static final long FLOOR_2 = 20;
    private static final long FLOOR_3 = 30;

    private final List<AuditEvent> audits = new ArrayList<>();
    private final AtomicInteger lookups = new AtomicInteger();

    private RoleChecker checker(AccessGrant grant) {
        return new RoleChecker((org, user) -> {
            lookups.incrementAndGet();
            return org == ORG_A ? grant : AccessGrant.none();
        }, audits::add);
    }

    @AfterEach
    void clear() {
        CurrentUserHolder.clear();
    }

    @Test
    @DisplayName("[IAM-04.04][AT-IAM-11.3] ANALYST가 기기 수동 제어 → 403 PERMISSION_DENIED, 감사 ACCESS_DENIED(permission=DEVICE_CONTROL)")
    void analystCannotControl() {
        // given
        CurrentUserHolder.set(new CurrentUser(7, ORG_A));
        RoleChecker checker = checker(AccessGrant.of(BuiltinRole.ANALYST, SpaceScope.all()));
        // when & then
        assertThatThrownBy(() -> checker.require(Permission.DEVICE_CONTROL, FLOOR_2))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.PERMISSION_DENIED);
        assertThat(audits).singleElement().satisfies(a -> {
            assertThat(a.action()).isEqualTo("ACCESS_DENIED");
            assertThat(a.result()).isEqualTo(AuditResult.DENIED);
            assertThat(a.organizationId()).isEqualTo(ORG_A);
            assertThat(a.actorId()).isEqualTo("7");
            assertThat(a.detail()).containsEntry("permission", "DEVICE_CONTROL").containsEntry("spaceId", "20");
        });
    }

    @Test
    @DisplayName("[IAM-04.01][AT-IAM-11.1] 권한이 있으면 통과하고 같은 요청에서는 권한을 한 번만 조회한다")
    void operatorCanControl() {
        CurrentUserHolder.set(new CurrentUser(7, ORG_A));
        RoleChecker checker = checker(AccessGrant.of(BuiltinRole.OPERATOR, SpaceScope.all()));
        assertThat(checker.require(Permission.DEVICE_CONTROL, FLOOR_2).role()).isEqualTo("OPERATOR");
        assertThat(checker.require(Permission.DEV_PLACE)).isNotNull();
        assertThat(checker.has(Permission.FLOW_DEPLOY_CONTROL)).isFalse();
        assertThat(lookups).hasValue(1);
        assertThat(audits).isEmpty();

        CurrentUserHolder.set(new CurrentUser(7, ORG_A)); // 다음 요청(새 신원 객체)은 다시 판정한다(BR-IAM-13)
        checker.grant();
        assertThat(lookups).hasValue(2);
    }

    @Test
    @DisplayName("[IAM-04.05][AT-IAM-11.2] 다른 조직 자원은 권한과 관계없이 404(도메인 코드), 감사하지 않는다")
    void otherOrganizationIsHidden() {
        CurrentUserHolder.set(new CurrentUser(7, ORG_A));
        RoleChecker checker = checker(AccessGrant.of(BuiltinRole.ADMIN, SpaceScope.all()));
        ErrorCode deviceNotFound = new TestNotFound();
        assertThatThrownBy(() -> checker.requireVisible(Permission.DEV_READ, ORG_B, null, deviceNotFound))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(deviceNotFound);
        assertThatCode(() -> checker.requireVisible(Permission.DEV_READ, ORG_A, FLOOR_3, deviceNotFound)).doesNotThrowAnyException();
        assertThat(audits).isEmpty();
    }

    @Test
    @DisplayName("[IAM-04.02][AT-IAM-09.4] 공간 범위 밖 자원은 역할보다 먼저 404로 숨기고, 범위 안이면 역할로 판정한다")
    void spaceScope() {
        CurrentUserHolder.set(new CurrentUser(7, ORG_A));
        RoleChecker checker = checker(AccessGrant.of(BuiltinRole.OPERATOR, SpaceScope.only(Set.of(FLOOR_2))));
        assertThatThrownBy(() -> checker.require(Permission.DEV_READ, FLOOR_3))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND);
        assertThatThrownBy(() -> checker.require(Permission.DEV_ADMIN, FLOOR_3))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND);
        assertThatThrownBy(() -> checker.require(Permission.DEV_ADMIN, FLOOR_2))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.PERMISSION_DENIED);
        assertThat(checker.require(Permission.DEV_READ, FLOOR_2)).isNotNull();
        assertThat(checker.require(Permission.DEV_READ, null)).isNotNull(); // 공간 없는 조직 단위 자원
        assertThat(checker.spaceScope().allowedSpaceIds()).containsExactly(FLOOR_2);
    }

    @Test
    @DisplayName("[IAM-04.05] 없는 사용자·다른 조직 신원은 권한 없음(기본 거부) → 403")
    void unknownUserDenied() {
        CurrentUserHolder.set(new CurrentUser(7, ORG_B));
        RoleChecker checker = checker(AccessGrant.of(BuiltinRole.ADMIN, SpaceScope.all()));
        assertThatThrownBy(() -> checker.require(Permission.DEV_READ))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.PERMISSION_DENIED);
        assertThatThrownBy(checker::requireAdmin).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("[IAM-04.07][BR-IAM-19] 장기 토큰은 역할 ∩ 범위 안에서만 허용되고, 화면 전용 작업은 범위와 관계없이 403")
    void accessTokenScopes() {
        CurrentUserHolder.set(new CurrentUser(7, ORG_A, 55L, Set.of("read:devices", "mcp:write")));
        RoleChecker checker = checker(AccessGrant.of(BuiltinRole.ADMIN, SpaceScope.all()));
        assertThat(checker.require(Permission.DEV_READ)).isNotNull();
        assertThat(checker.has(Permission.FLOW_WRITE)).isTrue();
        assertThatThrownBy(() -> checker.require(Permission.DEVICE_CONTROL)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(checker::requireInteractive)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.PERMISSION_DENIED);
        assertThat(audits).hasSize(2);
        assertThat(audits.get(0).detail()).containsEntry("accessTokenId", "55");
        assertThat(audits.get(1).detail()).containsEntry("reason", "ACCESS_TOKEN_NOT_ALLOWED");

        CurrentUserHolder.set(new CurrentUser(7, ORG_A));
        assertThatCode(checker::requireInteractive).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("감사 기록이 실패해도 응답은 403 그대로, 감사 창구가 없어도 동작한다")
    void auditFailureDoesNotChangeResponse() {
        CurrentUserHolder.set(new CurrentUser(7, ORG_A));
        RoleChecker failing = new RoleChecker((o, u) -> AccessGrant.none(), e -> {
            throw new IllegalStateException("core down");
        });
        assertThatThrownBy(() -> failing.require(Permission.DEV_READ))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.PERMISSION_DENIED);
        RoleChecker silent = new RoleChecker((o, u) -> AccessGrant.none(), null);
        assertThatThrownBy(() -> silent.require(Permission.DEV_READ)).isInstanceOf(BusinessException.class);
        assertThat(silent.currentUser().userId()).isEqualTo(7);
    }

    @Test
    @DisplayName("[IAM-04.03] 사용자 정의 역할은 고른 권한만 허용한다")
    void customRole() {
        CurrentUserHolder.set(new CurrentUser(7, ORG_A));
        RoleChecker checker = checker(AccessGrant.custom(Set.of(Permission.ALARM_HANDLE), null));
        assertThat(checker.grant().role()).isEqualTo("CUSTOM");
        assertThat(checker.has(Permission.ALARM_HANDLE)).isTrue();
        assertThat(checker.has(Permission.DEV_READ)).isFalse();
        assertThat(AccessGrant.custom(Set.of(), SpaceScope.all()).permissions()).isEmpty();
    }

    private static final class TestNotFound implements ErrorCode {
        @Override
        public String code() {
            return "DEVICE_NOT_FOUND";
        }

        @Override
        public int httpStatus() {
            return 404;
        }
    }

    @Test
    @DisplayName("[IAM-05.01][IAM-04.07] 장기 토큰 요청은 토큰 ID를 권한 원천에 넘기고, 같은 사용자의 웹 요청과 다른 판정을 받는다")
    void tokenIdReachesLookup() {
        // given: 웹 신원은 OPERATOR·전체, 토큰 501은 FLOOR_2만
        List<Long> seen = new ArrayList<>();
        RoleChecker checker = new RoleChecker(PermissionLookup.tokenAware((org, user, tokenId) -> {
            seen.add(tokenId);
            return tokenId == null ? AccessGrant.of(BuiltinRole.OPERATOR, SpaceScope.all())
                    : AccessGrant.of(BuiltinRole.OPERATOR, SpaceScope.only(Set.of(FLOOR_2)));
        }), audits::add);
        CurrentUserHolder.set(new CurrentUser(7, ORG_A));
        assertThat(checker.spaceScope().includes(FLOOR_3)).isTrue();
        // when
        CurrentUserHolder.set(new CurrentUser(7, ORG_A, 501L, Set.of("read:devices")));
        // then
        assertThat(checker.spaceScope().includes(FLOOR_3)).isFalse();
        assertThat(seen).containsExactly(null, 501L);
    }
}
