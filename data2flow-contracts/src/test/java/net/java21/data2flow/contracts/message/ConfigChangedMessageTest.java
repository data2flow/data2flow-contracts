package net.java21.data2flow.contracts.message;

import net.java21.data2flow.contracts.message.ConfigChangedMessage.EntityType;
import net.java21.data2flow.contracts.message.ConfigChangedMessage.Op;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** DSC-01.01·SCR-03.04·ING-03.01: 설정 변경 봉투 ConfigChangedMessage v1(EVT-DEV-04·EVT-DSC-01·EVT-SCR-01·EVT-ING-08) */
class ConfigChangedMessageTest {

    private final MessageCodec codec = MessageCodec.create();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-03T02:12:09Z"), ZoneOffset.UTC);

    /** architecture.md §4.5 예시 그대로 */
    private static final String DOC_EXAMPLE = """
            { "v": 1, "messageId": "6d3f8a2e-1b4c-4d5e-9f60-718293a4b5c6", "entityType": "DEVICE", "id": "1042", "version": 7,
              "op": "UPSERT", "orgId": "1", "at": "2026-10-03T02:12:09Z" }
            """;

    @Test
    @DisplayName("DSC-01.01 문서 예시가 config-changed.v1.json을 통과하고 읽힌다")
    void docExample() {
        MessageSchemas.assertValid(MessageSchemas.CONFIG_CHANGED, codec.mapper().readTree(DOC_EXAMPLE));
        ConfigChangedMessage m = codec.read(DOC_EXAMPLE, ConfigChangedMessage.class);
        assertThat(m.entityType()).isEqualTo(EntityType.DEVICE);
        assertThat(m.id()).isEqualTo("1042");
        assertThat(m.version()).isEqualTo(7);
        assertThat(m.op()).isEqualTo(Op.UPSERT);
        assertThat(m.orgId()).isEqualTo("1");
    }

    @Test
    @DisplayName("SCR-03.04 TC-SCR-050 SCRIPT 배포·SOURCE 삭제 메시지를 만들고 스키마를 통과한다")
    void factories() {
        ConfigChangedMessage script = ConfigChangedMessage.upsert(EntityType.SCRIPT, 42, 3, 1, clock);
        ConfigChangedMessage source = ConfigChangedMessage.delete(EntityType.SOURCE, 3, 9, 1, clock);
        MessageSchemas.assertValid(script);
        MessageSchemas.assertValid(source);
        assertThat(codec.writeAsString(script)).contains("\"entityType\":\"SCRIPT\"", "\"id\":\"42\"", "\"orgId\":\"1\"",
                "\"at\":\"2026-10-03T02:12:09Z\"");
        assertThat(codec.read(codec.write(source), ConfigChangedMessage.class)).isEqualTo(source);
        assertThat(source.op()).isEqualTo(Op.DELETE);
    }

    @Test
    @DisplayName("ING-03.01 TC-ING-047 이 코드가 모르는 entityType은 UNKNOWN으로 읽혀 소비자가 무시할 수 있다")
    void unknownEntityTypeIsTolerated() {
        ConfigChangedMessage m = codec.read(DOC_EXAMPLE.replace("\"DEVICE\"", "\"ROBOT_ARM\""), ConfigChangedMessage.class);
        assertThat(m.entityType()).isEqualTo(EntityType.UNKNOWN);
    }

    @Test
    @DisplayName("ACT-01.04 ACT-03.05 CAPABILITY(기능 이름)·DRIVER(드라이버 ID) 변경 메시지가 스키마를 통과하고 읽힌다")
    void capabilityAndDriverEntityTypes() {
        ConfigChangedMessage capability = new ConfigChangedMessage(ConfigChangedMessage.VERSION, java.util.UUID.randomUUID(),
                EntityType.CAPABILITY, "AirPurifierMode", 2, Op.UPSERT, "1", clock.instant());
        ConfigChangedMessage driver = ConfigChangedMessage.upsert(EntityType.DRIVER, 12, 4, 1, clock);
        MessageSchemas.assertValid(capability);
        MessageSchemas.assertValid(driver);
        assertThat(codec.read(codec.write(capability), ConfigChangedMessage.class)).isEqualTo(capability);
        assertThat(codec.read(DOC_EXAMPLE.replace("\"DEVICE\"", "\"DRIVER\""), ConfigChangedMessage.class).entityType())
                .isEqualTo(EntityType.DRIVER);
    }

    @Test
    @DisplayName("필수 필드가 없으면 MessageFormatException")
    void requiredFields() {
        assertThatThrownBy(() -> codec.read(DOC_EXAMPLE.replace("\"id\": \"1042\",", ""), ConfigChangedMessage.class))
                .isInstanceOf(MessageFormatException.class).hasMessageContaining("id");
        assertThatThrownBy(() -> codec.read(DOC_EXAMPLE.replace("\"op\": \"UPSERT\",", ""), ConfigChangedMessage.class))
                .isInstanceOf(MessageFormatException.class).hasMessageContaining("op");
    }
}
