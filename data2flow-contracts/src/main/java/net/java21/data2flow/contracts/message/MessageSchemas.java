package net.java21.data2flow.contracts.message;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 메시지 계약 JSON Schema({@code classpath:data2flow/contracts/schemas/{name}.v{version}.json}) 검증기.
 *
 * <p>계약 테스트에서 쓴다(TC-ING-033: 모든 디코더 출력이 {@code canonical-telemetry.v1.json}을 통과). 형식(date-time, uuid)도 검사한다.
 * 운영 코드 경로에서는 쓰지 않는다(검증 비용). networknt json-schema-validator(Apache-2.0)가 클래스패스에 있어야 하며
 * 테스트 키트 {@code data2flow-contracts-test}가 가져온다.
 */
public final class MessageSchemas {

    public static final String RAW_ENVELOPE = "raw-envelope.v1.json";
    public static final String CANONICAL_TELEMETRY = "canonical-telemetry.v1.json";
    public static final String CONFIG_CHANGED = "config-changed.v1.json";
    public static final String DOMAIN_EVENT = "domain-event.v1.json";

    private static final String BASE = "/data2flow/contracts/schemas/";
    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
            b -> b.schemaRegistryConfig(SchemaRegistryConfig.builder().formatAssertionsEnabled(true).build()));
    private static final Map<String, Schema> CACHE = new ConcurrentHashMap<>();
    private static final MessageCodec CODEC = MessageCodec.create();

    private MessageSchemas() {
    }

    /** 이 메시지 타입의 스키마 파일 이름({@code {name}.v{version}.json}) */
    public static String fileOf(Class<? extends Message> type) {
        MessageSchema schema = type.getAnnotation(MessageSchema.class);
        if (schema == null) {
            throw new IllegalArgumentException(type.getName() + "에 @MessageSchema가 없습니다");
        }
        return schema.name() + ".v" + schema.version() + ".json";
    }

    /** 스키마 원문(JSON). 다른 언어 소비자(analytics 등)에 넘기거나 문서로 쓸 때 */
    public static JsonNode raw(String file) {
        try (InputStream in = open(file)) {
            return CODEC.mapper().readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 위반 목록(경로 + 설명). 비어 있으면 통과 */
    public static List<String> validate(String file, JsonNode instance) {
        Schema schema = CACHE.computeIfAbsent(file, MessageSchemas::load);
        return schema.validate(instance).stream().map(Error::toString).toList();
    }

    /** 메시지를 직렬화한 JSON이 자기 타입의 스키마를 통과하는지 */
    public static List<String> validate(Message message) {
        @SuppressWarnings("unchecked")
        Class<? extends Message> type = (Class<? extends Message>) message.getClass();
        return validate(fileOf(type), CODEC.toTree(message));
    }

    /** 통과하지 않으면 {@link AssertionError} */
    public static void assertValid(String file, JsonNode instance) {
        List<String> errors = validate(file, instance);
        if (!errors.isEmpty()) {
            throw new AssertionError(file + " 위반: " + errors + "\n" + instance);
        }
    }

    public static void assertValid(Message message) {
        @SuppressWarnings("unchecked")
        Class<? extends Message> type = (Class<? extends Message>) message.getClass();
        assertValid(fileOf(type), CODEC.toTree(message));
    }

    private static Schema load(String file) {
        try (InputStream in = open(file)) {
            return REGISTRY.getSchema(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static InputStream open(String file) {
        InputStream in = MessageSchemas.class.getResourceAsStream(BASE + file);
        if (in == null) {
            throw new IllegalArgumentException("메시지 스키마가 없습니다: " + file);
        }
        return in;
    }
}
