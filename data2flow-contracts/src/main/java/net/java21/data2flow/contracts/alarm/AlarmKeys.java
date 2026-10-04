package net.java21.data2flow.contracts.alarm;

/**
 * 알람 키(alarms.alarm_key varchar(200), BR-RUL-02). 같은 키에는 열린 알람이 하나만 있으므로 생산자(flow-engine 알람 노드, core-api 시스템
 * 판정)와 알람 서비스가 같은 규칙으로 만들어야 한다.
 *
 * <ul>
 *   <li>규칙: {@code rule:{ruleId}:{targetKey}}</li>
 *   <li>플로우 알람 노드: {@code flow:{flowId}:{nodeId}:{targetKey}}(TC-FLW-054: flowId·nodeId·deviceId로 고정)</li>
 *   <li>시스템: {@code system:{code}:{refId}}(예: {@code system:DRIVER_CIRCUIT_OPEN:7}, {@code system:OSCILLATION:15:Thermostat})</li>
 * </ul>
 * 대상 키는 기기가 있으면 기기 ID, 없으면 {@code space-{spaceId}}다({@link #target}).
 */
public final class AlarmKeys {

    /** 열 길이 */
    public static final int MAX_LENGTH = 200;

    private AlarmKeys() {
    }

    public static String rule(long ruleId, String targetKey) {
        return check("rule:" + ruleId + ":" + require(targetKey, "targetKey"));
    }

    public static String flow(String flowId, String nodeId, String targetKey) {
        return check("flow:" + require(flowId, "flowId") + ":" + require(nodeId, "nodeId") + ":"
                + require(targetKey, "targetKey"));
    }

    /** 시스템 알람. {@code refParts}는 ':'로 잇는다 */
    public static String system(String code, String... refParts) {
        StringBuilder sb = new StringBuilder("system:").append(require(code, "code"));
        if (refParts.length == 0) {
            throw new IllegalArgumentException("시스템 알람 키에는 참조 ID가 하나 이상 필요합니다");
        }
        for (String part : refParts) {
            sb.append(':').append(require(part, "refId"));
        }
        return check(sb.toString());
    }

    /** 대상 키: 기기 ID, 기기가 없으면 {@code space-{spaceId}} */
    public static String target(Long deviceId, Long spaceId) {
        if (deviceId != null) {
            return Long.toString(deviceId);
        }
        if (spaceId != null) {
            return "space-" + spaceId;
        }
        throw new IllegalArgumentException("deviceId·spaceId 중 하나는 있어야 합니다");
    }

    /** 키가 어느 출처의 것인지. 형식이 틀리면 UNKNOWN */
    public static AlarmSourceType sourceOf(String alarmKey) {
        if (alarmKey == null) {
            return AlarmSourceType.UNKNOWN;
        }
        if (alarmKey.startsWith("rule:")) {
            return AlarmSourceType.RULE;
        }
        if (alarmKey.startsWith("flow:")) {
            return AlarmSourceType.FLOW;
        }
        if (alarmKey.startsWith("system:")) {
            return AlarmSourceType.SYSTEM;
        }
        return AlarmSourceType.UNKNOWN;
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "은(는) 비어 있을 수 없습니다");
        }
        return value;
    }

    private static String check(String key) {
        if (key.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("알람 키는 " + MAX_LENGTH + "자 이하입니다: " + key.length());
        }
        return key;
    }
}
