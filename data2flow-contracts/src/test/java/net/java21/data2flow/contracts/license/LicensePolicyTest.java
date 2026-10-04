package net.java21.data2flow.contracts.license;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** DSC-09.14 ADR-018 오픈소스 라이선스 판정(oss-stack.md §1 표) */
class LicensePolicyTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Apache-2.0|ALLOWED", "The Apache Software License, Version 2.0|ALLOWED", "MIT|ALLOWED", "BSD-3-Clause|ALLOWED",
            "PostgreSQL|ALLOWED", "UPL-1.0|ALLOWED", "EDL-1.0|ALLOWED", "Eclipse Distribution License - v 1.0|ALLOWED",
            "EPL-2.0|CONDITIONAL", "Eclipse Public License 2.0|CONDITIONAL", "MPL-2.0|CONDITIONAL", "LGPL-2.1-only|CONDITIONAL",
            "GPL-2.0-only WITH Classpath-exception-2.0|CONDITIONAL",
            "GPL-3.0-only|FORBIDDEN", "AGPL-3.0|FORBIDDEN", "SSPL-1.0|FORBIDDEN", "BUSL-1.1|FORBIDDEN", "RSALv2|FORBIDDEN",
            "Elastic-2.0|FORBIDDEN", "GFTC|FORBIDDEN", "Confluent Community License|FORBIDDEN",
            "EPL-2.0 OR GPL-2.0-only|CONDITIONAL", "(Apache-2.0 OR GPL-3.0-only)|ALLOWED", "MIT AND AGPL-3.0|FORBIDDEN",
            "Apache-2.0 AND EPL-2.0|CONDITIONAL", "WTFPL-ish|UNKNOWN", "' '|UNKNOWN"})
    @DisplayName("DSC-09.14 TC-DSC-318 SPDX 식 판정: 허용·조건부·금지·미확인, OR는 좋은 쪽, AND는 나쁜 쪽")
    void classify(String expression, LicensePolicy.Verdict expected) {
        assertThat(LicensePolicy.classify(expression)).isEqualTo(expected);
    }

    @Test
    @DisplayName("DSC-09.14 BR-DSC-23 커넥터 라이브러리 목록에서 금지·미확인만 골라낸다(0건이면 통과)")
    void violations() {
        Map<String, LicensePolicy.Verdict> v = LicensePolicy.violations(Map.of(
                "com.hivemq:hivemq-mqtt-client", "Apache-2.0",
                "org.eclipse.milo:sdk-client", "EPL-2.0",
                "com.infiniteautomation:bacnet4j", "GPL-3.0-only",
                "x:unknown", "Custom"));
        assertThat(v).containsOnlyKeys("com.infiniteautomation:bacnet4j", "x:unknown");
        assertThat(v.get("com.infiniteautomation:bacnet4j").passes()).isFalse();
        assertThat(LicensePolicy.classify(null)).isEqualTo(LicensePolicy.Verdict.UNKNOWN);
        assertThat(LicensePolicy.Verdict.CONDITIONAL.passes()).isTrue();
        assertThat(LicensePolicy.violations(Map.of("org.postgresql:postgresql", "BSD-2-Clause"))).isEmpty();
    }
}
