package net.java21.data2flow.contracts.web;

import net.java21.data2flow.contracts.error.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** NFR-08.01·ADR-037: 4개 언어 번들의 키와 자리표시자가 짝이 맞고, 공통 코드마다 문구가 있다 */
class MessageBundleParityTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+}");

    @Test
    @DisplayName("NFR-08.01 ko·en·ja·zh 키 집합이 같고 공통 코드 19개가 모두 있다")
    void sameKeys() throws Exception {
        Properties ko = load("ko");
        Set<String> expected = Arrays.stream(CommonErrorCode.values()).map(CommonErrorCode::messageKey).collect(Collectors.toSet());
        assertThat(ko.stringPropertyNames()).isEqualTo(expected);
        for (String lang : new String[]{"en", "ja", "zh"}) {
            Properties other = load(lang);
            assertThat(other.stringPropertyNames()).as(lang).isEqualTo(ko.stringPropertyNames());
            for (String key : ko.stringPropertyNames()) {
                assertThat(placeholders(other.getProperty(key))).as(lang + " " + key).isEqualTo(placeholders(ko.getProperty(key)));
                assertThat(other.getProperty(key)).as(lang + " " + key).isNotBlank();
            }
        }
    }

    private static Set<String> placeholders(String s) {
        Matcher m = PLACEHOLDER.matcher(s);
        java.util.HashSet<String> out = new java.util.HashSet<>();
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    private static Properties load(String lang) throws Exception {
        Properties p = new Properties();
        try (var in = MessageBundleParityTest.class.getResourceAsStream("/data2flow/contracts/messages_" + lang + ".properties")) {
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }
}
