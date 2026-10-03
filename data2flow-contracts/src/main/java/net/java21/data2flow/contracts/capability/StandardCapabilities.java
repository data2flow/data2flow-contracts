package net.java21.data2flow.contracts.capability;

import net.java21.data2flow.contracts.message.MessageCodec;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 표준 기능 카탈로그(ACT-01.02). 정의 원본은 JSON 파일 {@code classpath:data2flow/contracts/capabilities/{name}.json}이고
 * 웹·AI·analytics처럼 Java가 아닌 소비자도 같은 파일을 쓴다.
 *
 * <table>
 *   <caption>표준 기능 7종(ACT-api §4 API-ACT-25)</caption>
 *   <tr><th>기능</th><th>속성</th><th>명령 set 인자</th><th>Matter</th></tr>
 *   <tr><td>Switch</td><td>on: boolean</td><td>{on} 필수</td><td>OnOff</td></tr>
 *   <tr><td>Thermostat</td><td>mode: enum, targetTemperature: number(5~35, 0.5), currentTemperature(RO)</td><td>{mode?, targetTemperature?} 1개 이상</td><td>Thermostat</td></tr>
 *   <tr><td>FanSpeed</td><td>level: integer(0~모델 maxLevel), auto: boolean</td><td>{level?, auto?} 1개 이상</td><td>FanControl</td></tr>
 *   <tr><td>Ventilation</td><td>mode: enum(off,on,auto), level: integer(1~3)</td><td>{mode?, level?} 1개 이상</td><td>FanControl</td></tr>
 *   <tr><td>Dimmer</td><td>level: integer(0~100 %)</td><td>{level} 필수</td><td>LevelControl</td></tr>
 *   <tr><td>Lock</td><td>locked: boolean, battery: number(RO)</td><td>{locked} 필수</td><td>DoorLock</td></tr>
 *   <tr><td>Contact</td><td>open: boolean(RO)</td><td>(명령 없음)</td><td>BooleanState</td></tr>
 * </table>
 */
public final class StandardCapabilities {

    public static final String SWITCH = "Switch";
    public static final String THERMOSTAT = "Thermostat";
    public static final String FAN_SPEED = "FanSpeed";
    public static final String VENTILATION = "Ventilation";
    public static final String DIMMER = "Dimmer";
    public static final String LOCK = "Lock";
    public static final String CONTACT = "Contact";

    /** 표준 기능의 명령 이름(목표 상태 설정) */
    public static final String SET = "set";
    /** 사용자 정의 기능 이름 접두사(BR-ACT-22) */
    public static final String CUSTOM_PREFIX = "custom.";
    /** 기능 이름 최대 길이(capabilities.name varchar(64)) */
    public static final int MAX_NAME_LENGTH = 64;

    public static final List<String> NAMES = List.of(SWITCH, THERMOSTAT, FAN_SPEED, VENTILATION, DIMMER, LOCK, CONTACT);

    private static final String BASE = "/data2flow/contracts/capabilities/";
    private static final Pattern CUSTOM_NAME = Pattern.compile("^custom\\.[A-Za-z][A-Za-z0-9]*$");
    private static final Map<String, CapabilityDefinition> BY_NAME = load();

    private StandardCapabilities() {
    }

    /** 표준 기능 전체(위 표 순서) */
    public static List<CapabilityDefinition> all() {
        return List.copyOf(BY_NAME.values());
    }

    public static Optional<CapabilityDefinition> find(String name) {
        return Optional.ofNullable(BY_NAME.get(name));
    }

    /** 없으면 {@link IllegalArgumentException} */
    public static CapabilityDefinition get(String name) {
        return find(name).orElseThrow(() -> new IllegalArgumentException("표준 기능이 아닙니다: " + name));
    }

    /** 표준 기능 이름인지(대소문자 무시: {@code switch}도 예약어로 본다) */
    public static boolean isStandardName(String name) {
        return name != null && NAMES.stream().anyMatch(n -> n.equalsIgnoreCase(name));
    }

    /** 사용자 정의 기능 이름 규칙: {@code custom.} + 영문으로 시작하는 영숫자, 전체 64자 이하 */
    public static boolean isValidCustomName(String name) {
        return name != null && name.length() <= MAX_NAME_LENGTH && CUSTOM_NAME.matcher(name).matches();
    }

    /**
     * 사용자 정의 기능 이름을 검사한다(BR-ACT-22). 규칙에 맞지 않으면 {@link IllegalArgumentException}. core-api는 표준 이름이면
     * {@code CAPABILITY_NAME_RESERVED}(409), 형식 오류면 {@code INVALID_REQUEST}(400)로 바꾼다.
     */
    public static void requireCustomName(String name) {
        if (isStandardName(name)) {
            throw new IllegalArgumentException("표준 기능 이름은 쓸 수 없습니다: " + name);
        }
        if (!isValidCustomName(name)) {
            throw new IllegalArgumentException("사용자 정의 기능 이름은 custom.으로 시작해야 합니다: " + name);
        }
    }

    /** 표준 기능 정의 JSON 원문 경로(웹 픽스처·문서용) */
    public static String resourcePath(String name) {
        return BASE + name + ".json";
    }

    private static Map<String, CapabilityDefinition> load() {
        JsonMapper mapper = MessageCodec.newMapper();
        Map<String, CapabilityDefinition> map = new LinkedHashMap<>();
        for (String name : NAMES) {
            try (InputStream in = StandardCapabilities.class.getResourceAsStream(resourcePath(name))) {
                if (in == null) {
                    throw new IllegalStateException("표준 기능 정의 파일이 없습니다: " + resourcePath(name));
                }
                CapabilityDefinition d = mapper.readValue(in, CapabilityDefinition.class);
                if (!d.name().equals(name) || !d.standard()) {
                    throw new IllegalStateException("표준 기능 정의가 잘못됐습니다: " + resourcePath(name));
                }
                map.put(name, d);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return map;
    }
}
