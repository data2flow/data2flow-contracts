package net.java21.data2flow.contracts.message;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ING-02.01: MessageCodec이 형식 오류를 DLQ 대상 예외 하나로 모은다 */
class MessageCodecTest {

    private final MessageCodec codec = MessageCodec.create();

    @Test
    @DisplayName("ING-02.01 JSON이 아니거나 객체가 아닌 본문은 MessageFormatException")
    void malformedBodies() {
        assertThatThrownBy(() -> codec.read("not json", CanonicalTelemetry.class))
                .isInstanceOf(MessageFormatException.class).hasMessageContaining("JSON");
        assertThatThrownBy(() -> codec.read("[1,2]", CanonicalTelemetry.class))
                .isInstanceOf(MessageFormatException.class).hasMessageContaining("객체");
        assertThatThrownBy(() -> codec.readEvent("{\"v\":1".getBytes()))
                .isInstanceOf(MessageFormatException.class);
        assertThatThrownBy(() -> codec.read("{\"v\":1,\"messageId\":\"nope\"}", ConfigChangedMessage.class))
                .isInstanceOf(MessageFormatException.class).hasMessageContaining("messageId");
    }

    @Test
    @DisplayName("@MessageSchema가 없는 타입은 프로그래밍 오류로 본다")
    void requiresSchemaAnnotation() {
        record Plain(int v, UUID messageId) implements Message {
        }
        assertThatThrownBy(() -> codec.read("{\"v\":1}", Plain.class)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MessageSchemas.fileOf(Plain.class)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MessageSchemas.raw("nope.json")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("쓸 수 없는 메시지는 MessageFormatException")
    void unwritable() {
        record Broken(int v, UUID messageId) implements Message {
            public String getExploding() {
                throw new IllegalStateException("직렬화 중 실패");
            }
        }
        Broken broken = new Broken(1, UUID.randomUUID());
        assertThatThrownBy(() -> codec.write(broken)).isInstanceOf(MessageFormatException.class);
        assertThat(codec.mapper()).isNotNull();
    }
}
