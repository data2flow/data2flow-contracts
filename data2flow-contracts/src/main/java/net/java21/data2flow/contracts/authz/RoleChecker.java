package net.java21.data2flow.contracts.authz;

import net.java21.data2flow.contracts.audit.AuditActions;
import net.java21.data2flow.contracts.audit.AuditEvent;
import net.java21.data2flow.contracts.audit.AuditRecorder;
import net.java21.data2flow.contracts.audit.AuditResult;
import net.java21.data2flow.contracts.error.BusinessException;
import net.java21.data2flow.contracts.error.CommonErrorCode;
import net.java21.data2flow.contracts.error.ErrorCode;
import net.java21.data2flow.contracts.identity.CurrentUser;
import net.java21.data2flow.contracts.identity.CurrentUserHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 서비스 메서드 첫 줄에서 부르는 권한 검사기(design/auth.md §7, api-rules §7, crowfoot RoleChecker 준용).
 *
 * <pre>{@code
 * public DeviceResponse approve(long deviceId, ApproveDeviceRequest req) {
 *     Device device = devices.findByIdAndOrganizationId(deviceId, roleChecker.currentUser().organizationId())
 *             .orElseThrow(() -> new BusinessException(DeviceErrorCode.DEVICE_NOT_FOUND));
 *     roleChecker.require(Permission.DEV_PLACE, device.getSpaceId(), DeviceErrorCode.DEVICE_NOT_FOUND);
 *     ...
 * }
 * }</pre>
 *
 * 판정 순서와 응답:
 * <ol>
 *   <li>다른 조직의 자원, 권한 밖 공간의 자원 → 404(존재를 숨김, BR-IAM-01·BR-IAM-16). 도메인 코드(예: DEVICE_NOT_FOUND)를 넘길 수 있다.</li>
 *   <li>보이는 자원인데 역할 권한이 없거나, 장기 토큰 범위가 그 권한을 열지 않음 → 403 {@code PERMISSION_DENIED}.
 *       거부는 감사 {@code ACCESS_DENIED}(result=DENIED, detail.permission)로 남긴다(BR-IAM-17).</li>
 * </ol>
 * 권한은 매 요청 {@link PermissionLookup}으로 원천에서 판정한다. 한 요청 안에서는 처음 판정을 다시 쓴다.
 */
public class RoleChecker {

    private static final Logger log = LoggerFactory.getLogger(RoleChecker.class);

    private final PermissionLookup lookup;
    private final AuditRecorder auditRecorder;
    private final ThreadLocal<Memo> memo = new ThreadLocal<>();

    /**
     * @param lookup        서비스가 제공하는 권한 조회
     * @param auditRecorder 거부 감사 기록. 없으면 null(감사를 남기지 않음, 테스트·감사 비대상 서비스)
     */
    public RoleChecker(PermissionLookup lookup, AuditRecorder auditRecorder) {
        this.lookup = lookup;
        this.auditRecorder = auditRecorder;
    }

    /** 요청 사용자(gateway 신원) */
    public CurrentUser currentUser() {
        return CurrentUserHolder.get();
    }

    /** 요청 사용자의 현재 권한. 장기 토큰 요청이면 토큰 ID를 넘겨 토큰 주체의 권한으로 판정한다(IAM-05.01·IAM-04.07) */
    public AccessGrant grant() {
        CurrentUser user = currentUser();
        Memo cached = memo.get();
        if (cached != null && cached.user() == user) {
            return cached.grant();
        }
        AccessGrant grant = lookup.find(user.organizationId(), user.userId(), user.accessTokenId());
        memo.set(new Memo(user, grant));
        return grant;
    }

    /** 권한이 있는가(장기 토큰이면 범위까지). 화면용 표시·분기에 쓰고, 거부 응답은 {@link #require(Permission)}로 한다 */
    public boolean has(Permission permission) {
        CurrentUser user = currentUser();
        return grant().has(permission) && (!user.viaAccessToken() || ApiScope.allows(user.scopes(), permission));
    }

    /** 공간과 관계없는 권한 검사. 없으면 403 PERMISSION_DENIED */
    public AccessGrant require(Permission permission) {
        if (!has(permission)) {
            deny(permission, null);
        }
        return grant();
    }

    /** 공간에 속한 자원의 권한 검사. 범위 밖 공간이면 404 RESOURCE_NOT_FOUND, 권한이 없으면 403 */
    public AccessGrant require(Permission permission, Long spaceId) {
        return require(permission, spaceId, CommonErrorCode.RESOURCE_NOT_FOUND);
    }

    /** 공간에 속한 자원의 권한 검사. 범위 밖 공간이면 {@code notFound}(도메인 404 코드), 권한이 없으면 403 */
    public AccessGrant require(Permission permission, Long spaceId, ErrorCode notFound) {
        requireSpace(spaceId, notFound);
        if (!has(permission)) {
            deny(permission, spaceId);
        }
        return grant();
    }

    /** 조직·공간·권한을 한 번에: 자원을 조직 조건 없이 읽은 경우(내부 API 결과 등)에 쓴다 */
    public AccessGrant requireVisible(Permission permission, long resourceOrganizationId, Long spaceId, ErrorCode notFound) {
        requireSameOrganization(resourceOrganizationId, notFound);
        return require(permission, spaceId, notFound);
    }

    /** 다른 조직의 자원이면 404로 숨긴다(BR-IAM-01) */
    public void requireSameOrganization(long resourceOrganizationId, ErrorCode notFound) {
        if (resourceOrganizationId != currentUser().organizationId()) {
            throw new BusinessException(notFound);
        }
    }

    /** 권한 밖 공간이면 404로 숨긴다(BR-IAM-16) */
    public void requireSpace(Long spaceId, ErrorCode notFound) {
        if (!grant().spaceScope().includes(spaceId)) {
            throw new BusinessException(notFound);
        }
    }

    /** 조회 리포지토리에 넘길 공간 범위(IAM-04.06: 목록·검색·집계·내보내기·스트림) */
    public SpaceScope spaceScope() {
        return grant().spaceScope();
    }

    /** ADMIN 전용 기능(crowfoot AdminGuard). IAM_MANAGE는 사용자 정의 역할에 넣을 수 없으므로 ADMIN만 통과한다 */
    public AccessGrant requireAdmin() {
        return require(Permission.IAM_MANAGE);
    }

    /**
     * 사람이 화면에서 해야 하는 일(플로우·스크립트 적용, 회원·토큰 관리)에서 장기 토큰 요청을 막는다(BR-IAM-19).
     * 토큰 범위가 그 권한을 열더라도 403이다.
     */
    public void requireInteractive() {
        CurrentUser user = currentUser();
        if (user.viaAccessToken()) {
            recordDenied(user, null, null, "ACCESS_TOKEN_NOT_ALLOWED");
            throw new BusinessException(CommonErrorCode.PERMISSION_DENIED);
        }
    }

    private void deny(Permission permission, Long spaceId) {
        recordDenied(currentUser(), permission, spaceId, null);
        throw new BusinessException(CommonErrorCode.PERMISSION_DENIED);
    }

    private void recordDenied(CurrentUser user, Permission permission, Long spaceId, String reason) {
        if (auditRecorder == null) {
            return;
        }
        AuditEvent.Builder event = AuditEvent.builder(user.organizationId(), AuditActions.ACCESS_DENIED)
                .actor(user)
                .result(AuditResult.DENIED);
        if (permission != null) {
            event.detail("permission", permission.name());
        }
        if (spaceId != null) {
            event.detail("spaceId", Long.toString(spaceId));
        }
        if (reason != null) {
            event.detail("reason", reason);
        }
        try {
            auditRecorder.record(event.build());
        } catch (RuntimeException ex) {
            // 감사 실패가 403 응답을 바꾸지 않는다. 기록 실패는 운영 로그로 찾는다
            log.warn("ACCESS_DENIED 감사 기록 실패", ex);
        }
    }

    private record Memo(CurrentUser user, AccessGrant grant) {
    }
}
