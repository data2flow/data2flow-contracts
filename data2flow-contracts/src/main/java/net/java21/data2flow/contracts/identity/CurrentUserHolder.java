package net.java21.data2flow.contracts.identity;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * 요청 범위 신원 저장소(design/auth.md §7). 서블릿 서비스에서는 {@code GatewayIdentityFilter}가 요청마다 넣고 지운다.
 * 가상 스레드도 요청 하나가 스레드 하나를 쓰므로 ThreadLocal로 안전하다. 메시지 소비자·배치처럼 요청 밖에서
 * 사용자를 대신해 일할 때는 {@link #callAs(CurrentUser, Supplier)}로 범위를 정한다.
 */
public final class CurrentUserHolder {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private CurrentUserHolder() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    /** 현재 신원. 없으면 필터를 거치지 않은 경로이므로 프로그래밍 오류로 본다 */
    public static CurrentUser get() {
        CurrentUser user = HOLDER.get();
        if (user == null) {
            throw new IllegalStateException("신원이 없습니다. GatewayIdentityFilter를 거치지 않은 경로입니다");
        }
        return user;
    }

    /** 신원이 선택인 경로(내부 API, 공개 경로)용 */
    public static Optional<CurrentUser> find() {
        return Optional.ofNullable(HOLDER.get());
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 주어진 신원으로 작업을 실행하고 이전 상태로 되돌린다 */
    public static <T> T callAs(CurrentUser user, Supplier<T> work) {
        CurrentUser previous = HOLDER.get();
        HOLDER.set(user);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                HOLDER.remove();
            } else {
                HOLDER.set(previous);
            }
        }
    }
}
