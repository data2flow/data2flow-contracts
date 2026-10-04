package net.java21.data2flow.contracts.messaging;

import java.util.regex.Pattern;

/**
 * Super Stream 소비자 그룹 이름(EVT-ING-01·02, architecture.md §4.2). RabbitMQ Stream에서는 소비자 이름(name)이 그룹이며,
 * 같은 이름의 소비자끼리 파티션을 나눠 갖고(Single Active Consumer) 서버 측 오프셋을 함께 쓴다.
 *
 * <p>운영·staging은 vhost가 달라(ADR-030) 같은 이름을 쓴다. <b>로컬 개발은 모두 vhost {@code data2flow-dev}를 함께 쓰므로 개발자 이름을
 * 붙인다</b>(예: {@code pipeline-nhn}, deployment.md §8.2). 같은 그룹으로 두 명이 실행하면 파티션을 나눠 가져 메시지를 일부만 받는다.
 */
public final class ConsumerGroups {

    /** pipeline: {@code data2flow.raw} 처리 */
    public static final String PIPELINE = "pipeline";
    /** pipeline 재처리 소비자(ING-01.04): {@code data2flow.raw}를 시각·오프셋으로 다시 읽음 */
    public static final String PIPELINE_REPROCESS = "pipeline-reprocess";
    /** flow-engine: {@code data2flow.telemetry} */
    public static final String FLOW = "flow";
    /** analytics: {@code data2flow.telemetry} */
    public static final String ANALYTICS = "analytics";
    /** action 출력 연결(DSC-04.01, BR-DSC-19): {@code data2flow.telemetry}를 별도 소비자로 읽어 외부 MQTT·Webhook으로 전달 */
    public static final String ACTION_OUTPUT = "action-output";
    /** core-api 실시간 화면: {@code data2flow.telemetry}, 비SAC·최신부터(오프셋 저장 안 함) */
    public static final String CORE_LIVE = "core-live";

    private static final Pattern DEVELOPER = Pattern.compile("[a-z][a-z0-9]{0,19}");

    private ConsumerGroups() {
    }

    /**
     * 실행 환경에 맞는 그룹 이름.
     *
     * @param group     위 상수 중 하나
     * @param developer 로컬 개발자 이름(소문자 영숫자, 20자 이하). 운영·staging은 null 또는 빈 값
     */
    public static String of(String group, String developer) {
        if (group == null || group.isBlank()) {
            throw new IllegalArgumentException("그룹 이름이 비어 있습니다");
        }
        if (developer == null || developer.isBlank()) {
            return group;
        }
        if (!DEVELOPER.matcher(developer).matches()) {
            throw new IllegalArgumentException("개발자 이름은 소문자로 시작하는 소문자·숫자 20자 이하입니다: " + developer);
        }
        return group + "-" + developer;
    }
}
