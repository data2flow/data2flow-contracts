package net.java21.data2flow.contracts.message;

import java.util.Set;

/**
 * {@link RawEnvelope#sourceType()} 값. DSC {@code data_sources.type}과 같다(DSC domain-model §1, EVT-ING-01).
 *
 * <p>enum이 아니라 문자열인 이유: 새 소스 유형이 생겨도 이전 버전 소비자가 메시지를 읽을 수 있어야 한다(모르는 값은 그대로 보관).
 */
public final class SourceTypes {

    public static final String MQTT_SUBSCRIBE = "MQTT_SUBSCRIBE";
    public static final String PLATFORM_BROKER = "PLATFORM_BROKER";
    public static final String WEBHOOK = "WEBHOOK";
    public static final String SIMULATION = "SIMULATION";
    /** 커넥터 카탈로그(DSC-09). 세부 프로토콜은 소스의 connectorKey */
    public static final String CONNECTOR = "CONNECTOR";
    /** 엣지 게이트웨이(DSC-08) */
    public static final String EDGE = "EDGE";
    public static final String KMA_WEATHER = "KMA_WEATHER";
    public static final String AIRKOREA = "AIRKOREA";
    public static final String HOLIDAY = "HOLIDAY";
    public static final String ICAL = "ICAL";
    public static final String ONEM2M = "ONEM2M";
    public static final String OPCUA = "OPCUA";
    public static final String MODBUS_TCP = "MODBUS_TCP";

    /** 이 버전의 계약이 아는 값 전체 */
    public static final Set<String> KNOWN = Set.of(MQTT_SUBSCRIBE, PLATFORM_BROKER, WEBHOOK, SIMULATION, CONNECTOR, EDGE,
            KMA_WEATHER, AIRKOREA, HOLIDAY, ICAL, ONEM2M, OPCUA, MODBUS_TCP);

    private SourceTypes() {
    }
}
