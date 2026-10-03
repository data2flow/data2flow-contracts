package net.java21.data2flow.contracts.message;

import net.java21.data2flow.contracts.message.event.EventPayload;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/**
 * 메시지 계약 직렬화·역직렬화(design/conventions.md §3 "계약을 바꿀 때는 v를 올리고, 소비자가 이전 버전도 읽게 한다").
 *
 * <ul>
 *   <li>쓰기: JSON(UTF-8), 시각은 ISO-8601 UTC, {@code byte[]}는 base64, null 선택 필드는 생략.</li>
 *   <li>읽기: <b>모르는 필드는 무시</b>하고, 모르는 enum 값은 그 enum의 기본값(있으면)으로 읽는다. 생산자가 필드를 먼저 더해도
 *       소비자가 깨지지 않는다.</li>
 *   <li>버전: 본문 {@code v}가 1 이상, 그 타입의 {@link MessageSchema#version()} 이하일 때만 읽는다. 아니면
 *       {@link UnsupportedSchemaVersionException}(DLQ로 보내고 소비자 배포 뒤 재처리).</li>
 *   <li>형식 오류·필수 필드 누락은 {@link MessageFormatException}.</li>
 * </ul>
 *
 * <p>스레드 안전하다. 서비스마다 하나를 만들어 공유한다.
 */
public final class MessageCodec {

    private final JsonMapper mapper;

    private MessageCodec(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public static MessageCodec create() {
        return new MessageCodec(newMapper());
    }

    /** 메시지 계약 규칙(모르는 필드 무시 등)을 적용한 새 매퍼. 테스트나 직접 트리를 다룰 때 쓴다 */
    public static JsonMapper newMapper() {
        return JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    public JsonMapper mapper() {
        return mapper;
    }

    public byte[] write(Message message) {
        try {
            return mapper.writeValueAsBytes(message);
        } catch (JacksonException e) {
            throw new MessageFormatException("메시지를 JSON으로 쓸 수 없습니다: " + message.getClass().getSimpleName(), e);
        }
    }

    public String writeAsString(Message message) {
        return new String(write(message), StandardCharsets.UTF_8);
    }

    public JsonNode toTree(Message message) {
        return readTree(write(message));
    }

    public <T extends Message> T read(byte[] body, Class<T> type) {
        if (DomainEvent.class.equals(type)) {
            throw new IllegalArgumentException("도메인 이벤트는 readEvent로 읽습니다");
        }
        JsonNode tree = readTree(body);
        MessageSchema schema = schemaOf(type);
        checkVersion(tree, schema.name(), schema.version());
        return convert(tree, mapper.constructType(type));
    }

    public <T extends Message> T read(String body, Class<T> type) {
        return read(body.getBytes(StandardCharsets.UTF_8), type);
    }

    /**
     * 도메인 이벤트를 읽는다. 페이로드 타입은 본문 {@code type}(라우팅 키)으로 정한다.
     *
     * @throws MessageFormatException 이 코드가 모르는 {@code type}이면(구독 바인딩 설정 오류이거나 더 새 생산자)
     */
    public DomainEvent<? extends EventPayload> readEvent(byte[] body) {
        JsonNode tree = readTree(body);
        JsonNode typeNode = tree.get("type");
        if (typeNode == null || !typeNode.isString()) {
            throw new MessageFormatException("도메인 이벤트에 type이 없습니다");
        }
        EventType type = EventType.fromRoutingKey(typeNode.asString())
                .orElseThrow(() -> new MessageFormatException("모르는 이벤트 종류입니다: " + typeNode.asString()));
        checkVersion(tree, "domain-event:" + type.routingKey(), type.version());
        return convert(tree, mapper.getTypeFactory().constructParametricType(DomainEvent.class, type.payloadType()));
    }

    /** 페이로드 타입을 아는 소비자용. 본문 종류의 페이로드 타입이 다르면 {@link MessageFormatException} */
    @SuppressWarnings("unchecked")
    public <P extends EventPayload> DomainEvent<P> readEvent(byte[] body, Class<P> payloadType) {
        DomainEvent<? extends EventPayload> event = readEvent(body);
        if (!payloadType.isInstance(event.payload())) {
            throw new MessageFormatException(event.type() + " 페이로드는 " + payloadType.getSimpleName() + "가 아닙니다");
        }
        return (DomainEvent<P>) event;
    }

    /** 본문의 스키마 버전 {@code v}. 없거나 정수가 아니면 null */
    public Integer versionOf(byte[] body) {
        JsonNode v = readTree(body).get("v");
        return v != null && v.isInt() ? v.intValue() : null;
    }

    private JsonNode readTree(byte[] body) {
        try {
            JsonNode tree = mapper.readTree(body);
            if (tree == null || !tree.isObject()) {
                throw new MessageFormatException("메시지 본문은 JSON 객체여야 합니다");
            }
            return tree;
        } catch (JacksonException e) {
            throw new MessageFormatException("메시지 본문이 JSON이 아닙니다", e);
        }
    }

    private <T> T convert(JsonNode tree, JavaType type) {
        try {
            return mapper.treeToValue(tree, type);
        } catch (JacksonException e) {
            for (Throwable c = e; c != null; c = c.getCause()) {
                if (c instanceof MessageFormatException mfe) {
                    throw mfe;
                }
            }
            throw new MessageFormatException("메시지를 " + type.getRawClass().getSimpleName() + "로 읽을 수 없습니다("
                    + e.getPathReference() + "): " + e.getOriginalMessage(), e);
        }
    }

    private static void checkVersion(JsonNode tree, String schema, int supported) {
        JsonNode v = tree.get("v");
        Integer received = v != null && v.isInt() ? v.intValue() : null;
        if (received == null || received < 1 || received > supported) {
            throw new UnsupportedSchemaVersionException(schema, received, supported);
        }
    }

    private static MessageSchema schemaOf(Class<?> type) {
        MessageSchema schema = type.getAnnotation(MessageSchema.class);
        if (schema == null) {
            throw new IllegalArgumentException(type.getName() + "에 @MessageSchema가 없습니다");
        }
        return schema;
    }
}
