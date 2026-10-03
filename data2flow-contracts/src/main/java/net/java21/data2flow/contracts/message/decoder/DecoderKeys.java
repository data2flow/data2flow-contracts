package net.java21.data2flow.contracts.message.decoder;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 디코더 키(ING-02.02~04, TC-ING-032). 소스 설정 {@code decoder.type=builtin}은 키, {@code script}는 {@code script:{id}@v{n}} */
public final class DecoderKeys {

    public static final String CHIRPSTACK_V4 = "chirpstack-v4";
    public static final String GENERIC_JSON = "generic-json";
    public static final String SINGLE_VALUE = "single-value";

    private static final Pattern SCRIPT = Pattern.compile("script:(\\d+)@v(\\d+)");

    private DecoderKeys() {
    }

    /** DECODE 스크립트 디코더 키 */
    public static String script(long scriptId, int versionNo) {
        return "script:" + scriptId + "@v" + versionNo;
    }

    /** 스크립트 디코더 키를 (스크립트 ID, 버전 번호)로 푼다. 스크립트 키가 아니면 빈 값 */
    public static Optional<ScriptVersion> parseScript(String key) {
        Matcher m = key == null ? null : SCRIPT.matcher(key);
        if (m == null || !m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new ScriptVersion(Long.parseLong(m.group(1)), Integer.parseInt(m.group(2))));
    }

    public record ScriptVersion(long scriptId, int versionNo) {
    }
}
