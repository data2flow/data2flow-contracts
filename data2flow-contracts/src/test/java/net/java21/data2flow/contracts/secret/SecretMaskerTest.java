package net.java21.data2flow.contracts.secret;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonWriter;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** NFR-03.02·AT-NFR-08.2: 로그·감사·내보내기에서 비밀값 패턴(password=, 토큰 접두어, 인증 헤더)이 검출되지 않는다 */
class SecretMaskerTest {

    @Test
    @DisplayName("[NFR-03.02][AT-NFR-08.2] key=value·JSON 쌍·인증 헤더·JWT·장기 토큰·URI 비밀번호를 가린다")
    void masksPatterns() {
        String log = "connect password=hunter2 apiKey: sk-123 tokenId=55 "
                + "{\"clientSecret\":\"abc\\\"def\",\"name\":\"gw\"} Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI3In0.sig "
                + "auth=Basic dXNlcjpwYXNz key data2flow_AbCdEf123456 url=mqtts://ingest:s3cr3t@broker:8883 "
                + "jwt eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI3In0.c2lnbmF0dXJl";
        String masked = SecretMasker.mask(log);
        assertThat(masked).doesNotContain("hunter2", "sk-123", "abc", "def\"", "dXNlcjpwYXNz", "AbCdEf123456", "s3cr3t", "c2lnbmF0dXJl", "eyJzdWIiOiI3In0.sig")
                .contains("password=***", "apiKey: ***", "tokenId=55", "\"clientSecret\":\"***\"", "\"name\":\"gw\"",
                        "Bearer ***", "Basic ***", "data2flow_***", "mqtts://ingest:***@broker:8883");
        assertThat(SecretMasker.mask(null)).isNull();
        assertThat(SecretMasker.mask("")).isEmpty();
        assertThat(SecretMasker.mask("평범한 로그")).isEqualTo("평범한 로그");
    }

    @Test
    @DisplayName("[NFR-03.02] 키 이름 판정: 끝이 비밀값 단어면 비밀값, tokenId·tokenPrefix·idempotencyKey는 아님")
    void sensitiveKeys() {
        assertThat(List.of("password", "db_password", "client-secret", "accessToken", "apiKey", "credentials", "Authorization"))
                .allMatch(SecretMasker::isSensitiveKey);
        assertThat(List.of("tokenId", "tokenPrefix", "idempotencyKey", "name", "userId"))
                .noneMatch(SecretMasker::isSensitiveKey);
        assertThat(SecretMasker.isSensitiveKey(null)).isFalse();
    }

    @Test
    @DisplayName("[NFR-03.02] 구조 값(설정 내보내기·감사 detail)은 비밀값 키 아래 전체와 Secret을 가리고 나머지는 재귀로 본다")
    void masksStructures() {
        Map<String, Object> config = Map.of(
                "sources", List.of(Map.of("name", "mqtt", "password", "p1", "auth", Map.of("token", "t1"))),
                "llm", Map.of("apiKey", Secret.of("k1"), "model", "x"),
                "count", 3);
        Object masked = SecretMasker.maskValue(config);
        assertThat(masked.toString()).doesNotContain("p1", "t1", "k1").contains("mqtt", "model=x", "count=3");
        assertThat(SecretMasker.maskValue(Secret.of("s"))).isEqualTo("***");
    }

    @Test
    @DisplayName("[NFR-03.02][AT-NFR-08.2] JSON 구조화 로그 커스터마이저가 모든 문자열 값을 가린다")
    void structuredLogCustomizer() {
        JsonWriter<Map<String, String>> writer = JsonWriter.of(members -> {
            members.add("message", m -> m.get("message"));
            new SecretMaskingJsonMembersCustomizer().customize((JsonWriter.Members) members);
        });
        assertThat(writer.writeToString(Map.of("message", "login password=hunter2")))
                .contains("password=***").doesNotContain("hunter2");
    }

    @Test
    @DisplayName("[NFR-03.02] 패턴 로그용 Logback 변환기가 메시지를 가린다")
    void logbackConverter() {
        LoggingEvent event = new LoggingEvent("x", new LoggerContext().getLogger("t"), Level.INFO,
                "connect {} password={}", null, new Object[]{"broker", "hunter2"});
        assertThat(new SecretMaskingMessageConverter().convert(event)).isEqualTo("connect broker password=***");
    }
}
