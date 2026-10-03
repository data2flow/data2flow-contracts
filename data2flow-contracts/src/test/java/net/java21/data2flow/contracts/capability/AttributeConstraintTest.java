package net.java21.data2flow.contracts.capability;

import net.java21.data2flow.contracts.message.MessageCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ACT-01.03·06.04: 모델 제약과 조직 절대 한계(BR-ACT-09) */
class AttributeConstraintTest {

    private static final AttributeConstraint MODEL = AttributeConstraint.range(18, 30);

    @Test
    @DisplayName("ACT-06.04 TC-ACT-115 절대 한계 16~28은 모델 18~30보다 넓어 거부(LIMIT_WIDER_THAN_MODEL), 18~28은 허용")
    void limitMustBeNarrower() {
        assertThat(AttributeConstraint.range(16, 28).within(MODEL)).isFalse();
        assertThat(AttributeConstraint.range(18, 28).within(MODEL)).isTrue();
        assertThat(AttributeConstraint.range(18, 31).within(MODEL)).isFalse();
        assertThat(new AttributeConstraint(18d, null, null).within(MODEL)).isFalse();
        assertThat(new AttributeConstraint(null, 28d, null).within(MODEL)).isFalse();
        assertThat(MODEL.within(null)).isTrue();
        assertThat(MODEL.within(AttributeConstraint.NONE)).isTrue();
        AttributeConstraint modes = AttributeConstraint.oneOf(List.of("cool", "heat"));
        assertThat(AttributeConstraint.oneOf(List.of("cool")).within(modes)).isTrue();
        assertThat(AttributeConstraint.oneOf(List.of("cool", "dry")).within(modes)).isFalse();
        assertThat(AttributeConstraint.NONE.within(modes)).isFalse();
    }

    @Test
    @DisplayName("ACT-04.01 API-ACT-03 effectiveConstraints = 모델 제약 ∩ 조직 한계")
    void intersect() {
        assertThat(MODEL.intersect(AttributeConstraint.range(16, 28))).isEqualTo(AttributeConstraint.range(18, 28));
        assertThat(MODEL.intersect(null)).isEqualTo(MODEL);
        assertThat(AttributeConstraint.NONE.intersect(MODEL)).isEqualTo(MODEL);
        assertThat(MODEL.intersect(AttributeConstraint.NONE)).isEqualTo(MODEL);
        assertThat(AttributeConstraint.oneOf(List.of("off", "cool", "heat")).intersect(AttributeConstraint.oneOf(List.of("cool", "dry"))))
                .isEqualTo(AttributeConstraint.oneOf(List.of("cool")));
        assertThat(AttributeConstraint.oneOf(List.of("a")).intersect(AttributeConstraint.NONE).enumValues()).containsExactly("a");
        assertThat(AttributeConstraint.NONE.intersect(AttributeConstraint.oneOf(List.of("a"))).enumValues()).containsExactly("a");
        assertThatThrownBy(() -> AttributeConstraint.range(18, 20).intersect(AttributeConstraint.range(25, 30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(AttributeConstraint.NONE.isEmpty()).isTrue();
        assertThat(MODEL.isEmpty()).isFalse();
        assertThat(MODEL.allows(18)).isTrue();
        assertThat(MODEL.allows(30.01)).isFalse();
        assertThat(MODEL.allows("x")).isTrue();
    }

    @Test
    @DisplayName("ACT-01.03 모델 제약 JSON {targetTemperature:{min:18,max:30}, mode:{enum:[…]}}을 그대로 읽는다")
    void json() {
        JsonMapper mapper = MessageCodec.newMapper();
        assertThat(mapper.readValue("{\"min\":18,\"max\":30}", AttributeConstraint.class)).isEqualTo(MODEL);
        assertThat(mapper.writeValueAsString(AttributeConstraint.oneOf(List.of("cool")))).isEqualTo("{\"enum\":[\"cool\"]}");
    }
}
