package net.java21.data2flow.contracts.messaging;

import java.util.regex.Pattern;

/**
 * 외부 MQTT 브로커 client-id(CLAUDE.md §5, deployment.md §8.2, BR-DSC-01). 같은 ID로 접속하면 브로커가 먼저 붙은 연결을 끊으므로
 * 환경·인스턴스마다 고유해야 한다.
 *
 * <p>모양: {@code {base}-{env}-{n}}. 운영 {@code data2flow-ingress-prod-1}, staging {@code data2flow-ingress-stg-0},
 * 개발자 {@code data2flow-ingress-dev-nhn-1}. 연결 테스트는 {@code {base}-test-{난수}}(BR-DSC-07).
 */
public final class ClientIds {

    /** MQTT 3.1.1 서버가 반드시 받아야 하는 길이는 23자지만 대부분의 브로커는 더 받는다. 우리 상한은 128자(source_runtimes.client_id) */
    public static final int MAX_LENGTH = 128;
    private static final Pattern PART = Pattern.compile("[a-z0-9][a-z0-9-]*");

    private ClientIds() {
    }

    /** 운영·staging: {@code env}는 {@code prod}·{@code stg} */
    public static String of(String base, String env, int ordinal) {
        requirePart(base, "base");
        requirePart(env, "env");
        if (ordinal < 0) {
            throw new IllegalArgumentException("인스턴스 번호는 0 이상입니다: " + ordinal);
        }
        return limit(base + "-" + env + "-" + ordinal);
    }

    /** 로컬 개발자: {@code {base}-dev-{developer}-{n}} */
    public static String developer(String base, String developer, int ordinal) {
        requirePart(developer, "developer");
        return of(base, "dev-" + developer, ordinal);
    }

    /** 연결 테스트용(clean start, 세션 남기지 않음): {@code {base}-test-{suffix}} */
    public static String test(String base, String randomSuffix) {
        requirePart(base, "base");
        requirePart(randomSuffix, "randomSuffix");
        return limit(base + "-test-" + randomSuffix);
    }

    private static void requirePart(String value, String name) {
        if (value == null || !PART.matcher(value).matches()) {
            throw new IllegalArgumentException(name + "는 소문자·숫자·하이픈만 씁니다: " + value);
        }
    }

    private static String limit(String id) {
        if (id.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("client-id가 " + MAX_LENGTH + "자를 넘습니다: " + id);
        }
        return id;
    }
}
