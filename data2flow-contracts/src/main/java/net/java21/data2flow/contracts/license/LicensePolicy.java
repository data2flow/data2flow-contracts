package net.java21.data2flow.contracts.license;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 오픈소스 라이선스 정책(ADR-018, oss-stack.md §1) 판정기. 커넥터 라이브러리 라이선스 검사(DSC-09.14, BR-DSC-23: 금지 라이선스 0건),
 * 엣지 배포판 검사(DSC-08.06), 서비스 빌드의 third-party 목록 검사가 같은 판정을 쓴다.
 *
 * <p>입력은 SPDX 식별자나 간단한 식(OR·AND·WITH)이다. OR는 가장 좋은 쪽, AND는 가장 나쁜 쪽을 고른다. GPL에 Classpath 예외가
 * 붙으면(JDK) 조건부로 본다. 목록에 없는 라이선스는 {@link Verdict#UNKNOWN}(사람이 검토, ADR로 허용 목록에 추가)이다.
 */
public final class LicensePolicy {

    /** 판정 */
    public enum Verdict {
        /** 자유롭게 포함·수정 */
        ALLOWED,
        /** 약한 카피레프트: 수정하지 않고 그대로 사용 */
        CONDITIONAL,
        /** 목록에 없음: 검토 필요(빌드는 실패시킨다) */
        UNKNOWN,
        /** 제품에 포함 금지 */
        FORBIDDEN;

        /** 빌드를 통과하는가(허용·조건부) */
        public boolean passes() {
            return this == ALLOWED || this == CONDITIONAL;
        }
    }

    private static final Set<String> ALLOWED = Set.of("APACHE-2.0", "MIT", "MIT-0", "BSD-2-CLAUSE", "BSD-3-CLAUSE", "ISC",
            "POSTGRESQL", "UPL-1.0", "ZLIB", "CC0-1.0", "UNICODE-DFS-2016", "UNICODE-3.0", "EDL-1.0", "BSD-3-CLAUSE-CLEAR",
            "0BSD", "PUBLIC-DOMAIN");
    private static final Set<String> CONDITIONAL = Set.of("MPL-2.0", "EPL-1.0", "EPL-2.0", "LGPL-2.1", "LGPL-2.1-ONLY",
            "LGPL-2.1-OR-LATER", "LGPL-3.0", "LGPL-3.0-ONLY", "LGPL-3.0-OR-LATER", "CDDL-1.0", "CDDL-1.1");
    private static final List<String> FORBIDDEN_PREFIXES = List.of("AGPL-", "SSPL-", "BUSL-", "BSL-1.1", "ELASTIC-",
            "TIMESCALE", "TSL", "RSAL", "COMMONS-CLAUSE", "GPL-", "GFTC", "CONFLUENT-COMMUNITY");
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("APACHE LICENSE 2.0", "APACHE-2.0"), Map.entry("APACHE LICENSE, VERSION 2.0", "APACHE-2.0"),
            Map.entry("THE APACHE SOFTWARE LICENSE, VERSION 2.0", "APACHE-2.0"), Map.entry("APACHE 2.0", "APACHE-2.0"),
            Map.entry("THE MIT LICENSE", "MIT"), Map.entry("MIT LICENSE", "MIT"),
            Map.entry("BSD", "BSD-3-CLAUSE"), Map.entry("NEW BSD LICENSE", "BSD-3-CLAUSE"),
            Map.entry("ECLIPSE PUBLIC LICENSE 2.0", "EPL-2.0"), Map.entry("ECLIPSE PUBLIC LICENSE - V 2.0", "EPL-2.0"),
            Map.entry("ECLIPSE DISTRIBUTION LICENSE - V 1.0", "EDL-1.0"),
            Map.entry("UNIVERSAL PERMISSIVE LICENSE, VERSION 1.0", "UPL-1.0"),
            Map.entry("MOZILLA PUBLIC LICENSE 2.0", "MPL-2.0"), Map.entry("MOZILLA PUBLIC LICENSE, VERSION 2.0", "MPL-2.0"),
            Map.entry("THE POSTGRESQL LICENSE", "POSTGRESQL"));

    private LicensePolicy() {
    }

    /** 라이선스 식 하나를 판정한다 */
    public static Verdict classify(String expression) {
        if (expression == null || expression.isBlank()) {
            return Verdict.UNKNOWN;
        }
        String expr = expression.trim();
        if (expr.startsWith("(") && expr.endsWith(")")) {
            expr = expr.substring(1, expr.length() - 1).trim();
        }
        String[] ors = expr.split("(?i)\\s+OR\\s+");
        if (ors.length > 1) {
            Verdict best = Verdict.FORBIDDEN;
            for (String part : ors) {
                Verdict v = classify(part);
                if (v.ordinal() < best.ordinal()) {
                    best = v;
                }
            }
            return best;
        }
        String[] ands = expr.split("(?i)\\s+AND\\s+");
        if (ands.length > 1) {
            Verdict worst = Verdict.ALLOWED;
            for (String part : ands) {
                Verdict v = classify(part);
                if (v.ordinal() > worst.ordinal()) {
                    worst = v;
                }
            }
            return worst;
        }
        String[] with = expr.split("(?i)\\s+WITH\\s+", 2);
        String id = normalize(with[0]);
        if (with.length == 2 && id.startsWith("GPL-") && normalize(with[1]).startsWith("CLASSPATH-EXCEPTION")) {
            return Verdict.CONDITIONAL;
        }
        return single(id);
    }

    /**
     * 의존성 목록에서 통과하지 못하는 것(금지·미확인)만 돌려준다. 비어 있으면 통과(TC-DSC-318: 금지 라이선스 0건).
     *
     * @param licensesByArtifact {@code groupId:artifactId} → 라이선스 식
     */
    public static Map<String, Verdict> violations(Map<String, String> licensesByArtifact) {
        Map<String, Verdict> out = new TreeMap<>();
        licensesByArtifact.forEach((artifact, license) -> {
            Verdict v = classify(license);
            if (!v.passes()) {
                out.put(artifact, v);
            }
        });
        return out;
    }

    private static Verdict single(String id) {
        if (ALLOWED.contains(id)) {
            return Verdict.ALLOWED;
        }
        if (CONDITIONAL.contains(id)) {
            return Verdict.CONDITIONAL;
        }
        for (String prefix : FORBIDDEN_PREFIXES) {
            if (id.startsWith(prefix)) {
                return Verdict.FORBIDDEN;
            }
        }
        return Verdict.UNKNOWN;
    }

    private static String normalize(String raw) {
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        String alias = ALIASES.get(upper);
        if (alias != null) {
            return alias;
        }
        return upper.replace(' ', '-');
    }
}
