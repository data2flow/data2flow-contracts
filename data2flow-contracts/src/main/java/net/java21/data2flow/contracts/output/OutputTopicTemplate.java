package net.java21.data2flow.contracts.output;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 출력 연결 MQTT 토픽 템플릿(output_connections.target.topicTemplate, 예: {@code d2f/{spaceCode}/{deviceName}/{metric}}). 허용 변수는
 * {@value #ALLOWED_TEXT}뿐이고(TC-DSC-130), 그 밖의 변수는 저장 전에 거부한다(core-api 검증과 웹 폼이 같은 규칙). 값의 {@code /}·{@code +}·
 * {@code #}·공백은 {@code _}로 바꿔 토픽 계층과 와일드카드를 깨지 않게 한다.
 */
public final class OutputTopicTemplate {

    public static final String SPACE_CODE = "spaceCode";
    public static final String DEVICE_NAME = "deviceName";
    public static final String DEVICE_ID = "deviceId";
    public static final String METRIC = "metric";
    /** 허용 변수 */
    public static final Set<String> ALLOWED = Set.of(SPACE_CODE, DEVICE_NAME, DEVICE_ID, METRIC);
    static final String ALLOWED_TEXT = "{spaceCode}, {deviceName}, {deviceId}, {metric}";
    /** MQTT 토픽 최대 바이트 수(보수적으로) */
    public static final int MAX_LENGTH = 256;

    private static final Pattern VAR = Pattern.compile("\\{([^{}]*)}");
    private static final Pattern UNSAFE = Pattern.compile("[/+#\\s\\u0000]");

    private final String template;

    private OutputTopicTemplate(String template) {
        this.template = template;
    }

    /** 검사하고 만든다. 허용 밖 변수·와일드카드·빈 템플릿이면 {@link IllegalArgumentException} */
    public static OutputTopicTemplate of(String template) {
        Set<String> unknown = unknownVariables(template);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("허용되지 않은 변수: " + unknown + " (허용: " + ALLOWED_TEXT + ")");
        }
        String literal = VAR.matcher(template).replaceAll("");
        if (literal.contains("+") || literal.contains("#")) {
            throw new IllegalArgumentException("토픽 템플릿에 와일드카드(+, #)를 쓸 수 없습니다");
        }
        if (template.startsWith("$")) {
            throw new IllegalArgumentException("$로 시작하는 토픽은 쓸 수 없습니다");
        }
        return new OutputTopicTemplate(template);
    }

    /** 허용 밖 변수 이름(검증 오류 표시용). 템플릿이 비면 빈 이름 하나 */
    public static Set<String> unknownVariables(String template) {
        Set<String> unknown = new LinkedHashSet<>();
        if (template == null || template.isBlank()) {
            unknown.add("");
            return unknown;
        }
        Matcher m = VAR.matcher(template);
        while (m.find()) {
            if (!ALLOWED.contains(m.group(1))) {
                unknown.add(m.group(1));
            }
        }
        return unknown;
    }

    /** 템플릿에 쓰인 변수 */
    public Set<String> variables() {
        Set<String> used = new LinkedHashSet<>();
        Matcher m = VAR.matcher(template);
        while (m.find()) {
            used.add(m.group(1));
        }
        return used;
    }

    /** 변수를 채운다. 값이 없는 변수는 {@code _}가 된다 */
    public String render(Map<String, String> values) {
        Matcher m = VAR.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = values == null ? null : values.get(m.group(1));
            String safe = value == null || value.isEmpty() ? "_" : UNSAFE.matcher(value).replaceAll("_");
            m.appendReplacement(sb, Matcher.quoteReplacement(safe));
        }
        m.appendTail(sb);
        if (sb.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("토픽이 " + MAX_LENGTH + "자를 넘습니다");
        }
        return sb.toString();
    }

    /** 측정값마다 토픽이 달라지는가({@code {metric}} 사용) */
    public boolean perMetric() {
        return variables().contains(METRIC);
    }

    public String template() {
        return template;
    }

    @Override
    public String toString() {
        return template;
    }
}
