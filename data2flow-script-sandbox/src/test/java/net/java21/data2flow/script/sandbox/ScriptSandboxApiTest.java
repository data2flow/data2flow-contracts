package net.java21.data2flow.script.sandbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 공용 샌드박스 API(ADR-046): 진입 함수 이름·ctx 추가 필드는 호출하는 쪽이 정하고, 실행 결과는 예외 대신 {@link ScriptOutcome}에 담는다.
 * pipeline(decode·transform)과 flow-engine(JS 함수 노드, ctx.flow·ctx.node)이 함께 쓰는 경로를 확인한다.
 */
class ScriptSandboxApiTest {

    private static final String ECHO = "function transform(msg, ctx) { return msg; }";

    @Test
    @DisplayName("[SCR-02.01] 공격 코퍼스는 모두 42종(host 13·bypass 8·pollution 6·loop 5·memory 5·output 5)")
    void corpusHas42Attacks() {
        assertThat(ScriptSandboxHarness.attacks("")).hasSize(42);
        assertThat(Stream.of("host-", "bypass-", "pollution-", "loop-", "memory-", "output-")
                .mapToLong(p -> ScriptSandboxHarness.attacks(p).count()).sum()).isEqualTo(42);
    }

    @Test
    @DisplayName("[SCR-02.01] 진입 함수 decode도 같은 방식으로 부르고, 진입 함수가 없으면 SCRIPT_RUNTIME_ERROR")
    void decodeEntryAndMissingEntry() {
        ScriptOutcome decoded = ScriptSandboxHarness.decode(
                "function decode(input, ctx) { return {t: input.payload.a + ctx.config.k, now: ctx.util.now()}; }",
                "{\"payload\":{\"a\":1}}", "{\"config\":{\"k\":2}}");
        ScriptOutcome missing = ScriptSandboxHarness.decode(ECHO, "{}", "{}");

        assertThat(decoded.ok()).as("%s", decoded.failure()).isTrue();
        assertThat(decoded.output().get("t").asInt()).isEqualTo(3);
        assertThat(decoded.output().get("now").asString()).isEqualTo("2026-10-03T00:00:00Z");
        assertThat(missing.failure().code()).isEqualTo(ScriptErrorCode.SCRIPT_RUNTIME_ERROR);
        assertThat(missing.failure().message()).contains("decode");
    }

    @Test
    @DisplayName("[FLW-02] ctx 추가 필드(flow·node)는 config 바로 뒤에 읽기 전용으로 놓이고, 없으면 null. 기본 샌드박스에는 없다")
    void contextKeys() {
        try (ScriptSandbox sandbox = new ScriptSandbox(ScriptLimits.defaults(), List.of("flow", "node"))) {
            String code = """
                    function transform(msg, ctx) {
                      'use strict';
                      let frozen = false;
                      try { ctx.flow.id = 9; } catch (e) { frozen = true; }
                      return {keys: Object.keys(ctx), flow: ctx.flow, node: ctx.node, frozen: frozen, src: ctx.source};
                    }
                    """;
            ScriptOutcome outcome = sandbox.run("transform", code, "node.js", "{}",
                    "{\"config\":{},\"flow\":{\"id\":\"7\"},\"source\":{\"kind\":\"x\"}}", ScriptSandboxHarness.NOW);

            assertThat(outcome.ok()).as("%s", outcome.failure()).isTrue();
            assertThat(outcome.output().get("keys").valueStream().map(n -> n.asString()).toList())
                    .containsExactly("config", "flow", "node", "device", "last", "source", "modules", "util", "log", "window");
            assertThat(outcome.output().get("flow").get("id").asString()).isEqualTo("7");
            assertThat(outcome.output().get("node").isNull()).isTrue();
            assertThat(outcome.output().get("frozen").asBoolean()).isTrue();
            assertThat(outcome.output().get("src").get("kind").asString()).isEqualTo("x");
        }
        ScriptOutcome base = ScriptSandboxHarness.transform("function transform(msg, ctx) { return {keys: Object.keys(ctx)}; }");
        assertThat(base.output().get("keys").valueStream().map(n -> n.asString()).toList())
                .containsExactly("config", "device", "last", "source", "modules", "util", "log", "window");
    }

    @Test
    @DisplayName("[FLW-02] ctx 추가 필드 이름은 영문 식별자이고 기본 필드와 겹치지 않아야 한다")
    void invalidContextKeys() {
        for (String key : new String[]{"config", "util", "a-b", "", "1x", "a\"b"}) {
            assertThatThrownBy(() -> new ScriptSandbox(ScriptLimits.defaults(), List.of(key)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("[SCR-02.01] BR-SCR-14 코드 64KB 초과·null 코드는 실행하지 않고 SCRIPT_RUNTIME_ERROR")
    void codeTooLarge() {
        ScriptOutcome big = ScriptSandboxHarness.transform("//" + "x".repeat(65_536) + "\n" + ECHO);
        ScriptOutcome none = ScriptSandboxHarness.sandbox().run("transform", null, "s.js", "{}", "{}", ScriptSandboxHarness.NOW);

        assertThat(big.failure().code()).isEqualTo(ScriptErrorCode.SCRIPT_RUNTIME_ERROR);
        assertThat(big.failure().message()).contains("65536");
        assertThat(none.failure().code()).isEqualTo(ScriptErrorCode.SCRIPT_RUNTIME_ERROR);
    }

    @Test
    @DisplayName("[SCR-02.01] BR-SCR-05 import 구문은 실행 전에 SCRIPT_FORBIDDEN_API(줄 번호 포함)")
    void moduleSyntaxIsForbidden() {
        ScriptOutcome outcome = ScriptSandboxHarness.transform("// a\nimport x from 'y';\n" + ECHO);

        assertThat(outcome.failure().code()).isEqualTo(ScriptErrorCode.SCRIPT_FORBIDDEN_API);
        assertThat(outcome.failure().line()).isEqualTo(2);
    }

    @Test
    @DisplayName("[SCR-02.01] 문법 검사: 정상이면 null, 오류면 줄·열(1번 줄 열은 엄격 모드 접두어만큼 보정)")
    void syntaxCheck() {
        ScriptSandbox sandbox = ScriptSandboxHarness.sandbox();

        assertThat(sandbox.syntaxCheck(ECHO, "a.js")).isNull();
        ScriptFailure failure = sandbox.syntaxCheck("function transform(msg, ctx) {\n  return ;;; )\n}", "a.js");
        assertThat(failure.code()).isEqualTo(ScriptErrorCode.SCRIPT_RUNTIME_ERROR);
        assertThat(failure.line()).isEqualTo(2);
    }

    @Test
    @DisplayName("[SCR-02.01] 실행 중 예외는 사용자 코드 줄·열과 함께 SCRIPT_RUNTIME_ERROR, 로그는 실패해도 남는다")
    void runtimeErrorLocationAndLogs() {
        ScriptOutcome outcome = ScriptSandboxHarness.transform(
                "function transform(msg, ctx) {\n  ctx.log('before');\n  null.x;\n}");

        assertThat(outcome.failure().code()).isEqualTo(ScriptErrorCode.SCRIPT_RUNTIME_ERROR);
        assertThat(outcome.failure().line()).isEqualTo(3);
        assertThat(outcome.logs()).anyMatch(l -> l.contains("before"));
        ScriptOutcome first = ScriptSandboxHarness.transform("function transform(msg, ctx) { throw new Error('x'); }");
        assertThat(first.failure().line()).isEqualTo(1);
        assertThat(first.failure().col()).isGreaterThan(1);
    }

    @Test
    @DisplayName("[SCR-02.02] BR-SCR-06 반환값: null은 버리기, 함수·Promise·NaN은 거부, Date는 ISO-8601, undefined 멤버는 건너뜀")
    void outputConversion() {
        assertThat(ScriptSandboxHarness.transform("function transform(msg, ctx) { return null; }").returnedNull()).isTrue();
        assertThat(ScriptSandboxHarness.transform(ECHO).returnedNull()).isFalse();
        assertThat(ScriptSandboxHarness.transform("function transform(msg, ctx) { return {n: NaN}; }").failure().code())
                .isEqualTo(ScriptErrorCode.SCRIPT_OUTPUT_INVALID);
        assertThat(ScriptSandboxHarness.transform("async function transform(msg, ctx) { return msg; }").failure().code())
                .isEqualTo(ScriptErrorCode.SCRIPT_FORBIDDEN_API);
        ScriptOutcome converted = ScriptSandboxHarness.transform("""
                function transform(msg, ctx) {
                  return {d: new Date(Date.UTC(2026, 0, 2, 3, 4, 5)), u: undefined, f: function () {}, b: true, x: 1.5,
                          big: 2 ** 60, s: 'a', a: [1, null]};
                }
                """);
        assertThat(converted.ok()).as("%s", converted.failure()).isTrue();
        assertThat(converted.output().get("d").asString()).isEqualTo("2026-01-02T03:04:05Z");
        assertThat(converted.output().has("u")).isFalse();
        assertThat(converted.output().has("f")).isFalse();
        assertThat(converted.output().get("b").asBoolean()).isTrue();
        assertThat(converted.output().get("x").asDouble()).isEqualTo(1.5);
        assertThat(converted.output().get("a").get(1).isNull()).isTrue();
        assertThat(converted.outputBytes()).isPositive();
    }

    @Test
    @DisplayName("[SCR-02.02] 거대 배열 반환은 크기 예산에서 바로 거부")
    void hugeArrayOutput() {
        ScriptOutcome outcome = ScriptSandboxHarness.transform(
                "function transform(msg, ctx) { return new Array(100000).fill(0); }");

        assertThat(outcome.failure().code()).isEqualTo(ScriptErrorCode.SCRIPT_OUTPUT_INVALID);
    }

    @Test
    @DisplayName("[SCR-02.02] 문장 수 한도를 넘으면 SCRIPT_TIMEOUT(문장 수)")
    void statementLimit() {
        ScriptLimits d = ScriptLimits.defaults();
        ScriptLimits small = new ScriptLimits(d.cpuTime().multipliedBy(20), d.wallTime().multipliedBy(5), 1_000,
                d.maxOutputBytes(), d.maxLogBytes(), d.maxLogEntries(), d.maxStringLength(), d.maxArrayLength(),
                d.maxCodeBytes(), d.maxOutputDepth());
        try (ScriptSandbox sandbox = new ScriptSandbox(small)) {
            ScriptOutcome outcome = sandbox.run("transform",
                    "function transform(msg, ctx) { let n = 0; for (let i = 0; i < 100000; i++) { n += i; } return {n}; }",
                    "s.js", "{}", "{}", ScriptSandboxHarness.NOW);

            assertThat(outcome.failure().code()).isEqualTo(ScriptErrorCode.SCRIPT_TIMEOUT);
            assertThat(outcome.failure().message()).contains("1000");
            assertThat(sandbox.limits()).isEqualTo(small);
        }
    }

    @Test
    @DisplayName("[SCR-02.02] CPU 시간 초과 재시도(ADR-046): 전에 성공한 코드는 최대 2회 다시 실행하고, 시간 초과로 끝난 코드는 다시 실행하지 않는다")
    void timeoutRetries() {
        // 같은 코드가 입력에 따라 무한 루프에 빠진다(재시도 기록은 코드 단위). 반복마다 네이티브 연산을 해서 문장 수 한도가 아니라
        // CPU 한도에 걸리게 한다(재시도는 CPU 시간 초과에만 한다)
        String code = "/* retry-test */ function transform(msg, ctx) { if (msg.loop) { const s = '[' + '1,'.repeat(20000) + '1]';"
                + " while (true) { JSON.parse(s); } } return msg; }";
        long cpuMs = ScriptLimits.defaults().cpuTime().toMillis();

        assertThat(ScriptSandboxHarness.transform(code, "{}", "{}").ok()).isTrue();
        ScriptOutcome retried = ScriptSandboxHarness.transform(code, "{\"loop\":true}", "{}");
        assertThat(retried.failure().code()).isEqualTo(ScriptErrorCode.SCRIPT_TIMEOUT);
        assertThat(retried.failure().message()).contains("[cpu");
        assertThat(retried.durationMs()).as("처음 + 재시도 2회, 실행마다 CPU 한도를 다 쓴다")
                .isGreaterThanOrEqualTo((1 + ScriptSandbox.TIMEOUT_RETRIES) * (double) cpuMs);
        assertThat(ScriptSandbox.TIMEOUT_RETRIES).isEqualTo(2);

        ScriptOutcome again = ScriptSandboxHarness.transform(code, "{\"loop\":true}", "{}");
        assertThat(again.failure().code()).isEqualTo(ScriptErrorCode.SCRIPT_TIMEOUT);
        assertThat(again.durationMs()).as("시간 초과로 끝난 코드는 재시도하지 않는다").isLessThan(retried.durationMs());
        assertThat(ScriptSandboxHarness.transform(code, "{}", "{}").ok()).isTrue();
    }

    @Test
    @DisplayName("[SCR-02.02] 닫은 뒤에는 쓸 수 없고, 실패 메시지는 500자로 자른다")
    void closeAndTruncate() {
        ScriptSandbox sandbox = new ScriptSandbox(ScriptLimits.defaults());
        sandbox.close();

        assertThatThrownBy(() -> sandbox.run("transform", ECHO, "s.js", "{}", "{}", ScriptSandboxHarness.NOW))
                .isInstanceOf(RuntimeException.class);
        assertThat(ScriptFailure.truncate(null)).isEmpty();
        assertThat(ScriptFailure.truncate("x".repeat(600))).hasSize(500);
        assertThat(ScriptFailure.of(ScriptErrorCode.SCRIPT_TIMEOUT, "m").line()).isNull();
        assertThat(new ScriptOutcome(null, null, null, 0, 0).logs()).isEmpty();
        assertThat(ScriptSandbox.WarmUpPolicy.defaults().target()).isEqualTo(Duration.ofMillis(10));
    }
}
