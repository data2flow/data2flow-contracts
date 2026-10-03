package net.java21.data2flow.contracts.secret;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 로그·감사 상세·설정 내보내기·진단 묶음에서 비밀값을 가린다(NFR-03.02, BR-IAM-21, AT-NFR-08.2).
 *
 * <p>가리는 대상:
 * <ul>
 *   <li>이름이 비밀값인 키의 값: {@code password=…}, {@code "apiKey":"…"}, {@code clientSecret: …}
 *       (키 이름을 소문자로 바꾸고 {@code -_}를 뺀 뒤 password·passwd·pwd·passphrase·secret·token·apikey·
 *       accesskey·secretkey·privatekey·credential(s)·authorization·cookie로 끝나면 비밀값으로 본다.
 *       {@code tokenId}, {@code tokenPrefix}처럼 뒤에 다른 말이 붙은 이름은 비밀값이 아니다)</li>
 *   <li>{@code Bearer …}, {@code Basic …} 인증 값, JWT 모양 문자열, 장기 토큰 접두어 {@code data2flow_…}</li>
 *   <li>URI 사용자 정보의 비밀번호 {@code scheme://user:pass@host}</li>
 * </ul>
 */
public final class SecretMasker {

    private static final List<String> SENSITIVE_SUFFIXES = List.of(
            "password", "passwd", "pwd", "passphrase", "secret", "token", "apikey", "accesskey", "secretkey",
            "privatekey", "credential", "credentials", "authorization", "cookie");

    private static final String KEY_CHARS = "[A-Za-z0-9_.-]*";
    private static final String KEY_WORDS =
            "(?:password|passwd|pwd|passphrase|secret|token|api[_-]?key|access[_-]?key|secret[_-]?key|private[_-]?key"
                    + "|credentials?|authorization|cookie)";

    private static final Pattern JSON_PAIR = Pattern.compile(
            "(\"" + KEY_CHARS + KEY_WORDS + "\"\\s*:\\s*)\"(?:[^\"\\\\]|\\\\.)*\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTH_SCHEME = Pattern.compile(
            "\\b(Bearer|Basic)\\s+[A-Za-z0-9._~+/=-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?<![A-Za-z0-9])(" + KEY_CHARS + KEY_WORDS + ")(\\s*[=:]\\s*)(?!\\*\\*\\*)(?!Bearer\\b|Basic\\b)([^\\s,;&\"'}]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]+");
    private static final Pattern PLATFORM_TOKEN = Pattern.compile("\\bdata2flow_[A-Za-z0-9_-]{8,}");
    private static final Pattern URI_USERINFO = Pattern.compile("(://[^/\\s:@]+:)[^@\\s/]+@");

    private SecretMasker() {
    }

    /** 키 이름이 비밀값을 뜻하는가 */
    public static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        return SENSITIVE_SUFFIXES.stream().anyMatch(normalized::endsWith);
    }

    /** 자유 문장(로그 메시지, 예외 문구)에서 비밀값을 가린다 */
    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = JSON_PAIR.matcher(text).replaceAll("$1\"" + Matcher.quoteReplacement(Secret.MASK) + "\"");
        out = AUTH_SCHEME.matcher(out).replaceAll("$1 " + Matcher.quoteReplacement(Secret.MASK));
        out = KEY_VALUE.matcher(out).replaceAll("$1$2" + Matcher.quoteReplacement(Secret.MASK));
        out = JWT.matcher(out).replaceAll(Matcher.quoteReplacement(Secret.MASK));
        out = PLATFORM_TOKEN.matcher(out).replaceAll("data2flow_" + Matcher.quoteReplacement(Secret.MASK));
        return URI_USERINFO.matcher(out).replaceAll("$1" + Matcher.quoteReplacement(Secret.MASK) + "@");
    }

    /**
     * 구조화된 값(감사 detail, 설정 내보내기)을 복사하면서 가린다. 비밀값 키 아래 값 전체와 {@link Secret}은 {@value Secret#MASK},
     * 문자열은 {@link #mask(String)}, Map·컬렉션은 재귀로 처리한다.
     */
    public static Object maskValue(Object value) {
        if (value instanceof Secret) {
            return Secret.MASK;
        }
        if (value instanceof String s) {
            return mask(s);
        }
        if (value instanceof Map<?, ?> map) {
            return maskMap(map);
        }
        if (value instanceof Collection<?> c) {
            return c.stream().map(SecretMasker::maskValue).toList();
        }
        return value;
    }

    public static Map<String, Object> maskMap(Map<?, ?> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        map.forEach((k, v) -> {
            String key = String.valueOf(k);
            // 비밀값 키 아래는 구조와 관계없이 통째로 가린다(예: {"password":{"before":…,"after":…}})
            out.put(key, isSensitiveKey(key) && v != null ? Secret.MASK : maskValue(v));
        });
        return out;
    }
}
