package net.java21.data2flow.script.sandbox;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

/**
 * SCR test-plan "샌드박스 하네스": 운영 기본 한도({@link ScriptLimits#defaults()}, pipeline·flow-engine application.yml과 같은 값)와
 * 운영 예열 정책({@link ScriptSandbox.WarmUpPolicy#defaults()})으로 만든 {@link ScriptSandbox}. 공격 코퍼스는
 * {@code src/test/resources/script-attacks/*.js} 파일 하나가 공격 하나다(첫 줄 {@code // expect: 코드|코드}, 42종).
 */
final class ScriptSandboxHarness {

    static final String NORMAL_INPUT = "{\"v\":1,\"deviceId\":17,\"measuredAt\":\"2026-10-03T00:00:00Z\","
            + "\"metrics\":[{\"key\":\"temperature\",\"value\":22.04,\"unit\":\"℃\",\"quality\":0}]}";
    static final Instant NOW = Instant.parse("2026-10-03T00:00:00Z");

    private static final ScriptSandbox SANDBOX = new ScriptSandbox(ScriptLimits.defaults());

    static {
        SANDBOX.warmUp(ScriptSandbox.WarmUpPolicy.defaults());
    }

    private ScriptSandboxHarness() {
    }

    static ScriptSandbox sandbox() {
        return SANDBOX;
    }

    static ScriptOutcome transform(String code) {
        return transform(code, NORMAL_INPUT, "{}");
    }

    static ScriptOutcome transform(String code, String input, String ctx) {
        return SANDBOX.run("transform", code, "script.js", input, ctx, NOW);
    }

    static ScriptOutcome decode(String code, String input, String ctx) {
        return SANDBOX.run("decode", code, "script.js", input, ctx, NOW);
    }

    /** 공격 코퍼스 중 이름이 prefix로 시작하는 파일 */
    static Stream<Attack> attacks(String prefix) {
        try {
            Path dir = Path.of(ScriptSandboxHarness.class.getResource("/script-attacks").toURI());
            try (Stream<Path> files = Files.list(dir)) {
                return files.filter(p -> p.getFileName().toString().startsWith(prefix))
                        .sorted()
                        .map(ScriptSandboxHarness::attack)
                        .toList()
                        .stream();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Attack attack(Path file) {
        try {
            String code = Files.readString(file, StandardCharsets.UTF_8);
            String first = code.lines().findFirst().orElse("");
            List<String> expected = first.startsWith("// expect:")
                    ? List.of(first.substring("// expect:".length()).trim().split("\\|"))
                    : List.of();
            return new Attack(file.getFileName().toString(), code, expected);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 공격 하나: 파일 이름, 코드, 허용 결과(OK 또는 오류 코드) */
    record Attack(String name, String code, List<String> expected) {

        String resultOf(ScriptOutcome outcome) {
            return outcome.ok() ? "OK" : outcome.failure().code().name();
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
