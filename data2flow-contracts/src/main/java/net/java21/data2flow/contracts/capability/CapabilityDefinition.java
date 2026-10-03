package net.java21.data2flow.contracts.capability;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 기능(Capability) 정의(ACT-01.01, ADR-009): 이름, 버전, 상태 속성, 명령, 기대 효과. 벤더와 무관한 표준 계약이고 화면 컨트롤·플로우
 * 제어 노드·AI 도구가 같은 정의를 쓴다. JSON 모양은 API-ACT-25 응답과 같고 JSON Schema는
 * {@code classpath:data2flow/contracts/schemas/capability-definition.v1.json}이다.
 *
 * @param name            기능 이름. 표준은 {@code Switch} 등, 사용자 정의는 {@code custom.} 접두사(BR-ACT-22)
 * @param version         정의 버전
 * @param standard        표준 기능이면 true(수정 불가)
 * @param matterCluster   대응 Matter 클러스터. 없으면 null
 * @param attributes      상태 속성
 * @param commands        명령
 * @param expectedEffects 제어 효과 기대값(없으면 빈 목록)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CapabilityDefinition(String name, int version, boolean standard, String matterCluster,
                                   List<CapabilityAttribute> attributes, List<CapabilityCommand> commands,
                                   List<ExpectedEffect> expectedEffects) {

    public CapabilityDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("기능 이름은 필수입니다");
        }
        if (version < 1) {
            throw new IllegalArgumentException(name + ": version은 1 이상이어야 합니다");
        }
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
        commands = commands == null ? List.of() : List.copyOf(commands);
        expectedEffects = expectedEffects == null ? List.of() : List.copyOf(expectedEffects);
        Set<String> names = new HashSet<>();
        for (CapabilityAttribute a : attributes) {
            if (!names.add(a.name())) {
                throw new IllegalArgumentException(name + ": 속성 이름이 겹칩니다: " + a.name());
            }
        }
        Set<String> commandNames = new HashSet<>();
        for (CapabilityCommand c : commands) {
            if (!commandNames.add(c.name())) {
                throw new IllegalArgumentException(name + ": 명령 이름이 겹칩니다: " + c.name());
            }
            for (String set : c.sets()) {
                CapabilityAttribute target = attributes.stream().filter(a -> a.name().equals(set)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(name + "." + c.name() + ": 없는 속성을 설정합니다: " + set));
                if (target.readOnly()) {
                    throw new IllegalArgumentException(name + "." + c.name() + ": 읽기 전용 속성은 설정할 수 없습니다: " + set);
                }
            }
        }
    }

    public Optional<CapabilityAttribute> attribute(String attributeName) {
        return attributes.stream().filter(a -> a.name().equals(attributeName)).findFirst();
    }

    public Optional<CapabilityCommand> command(String commandName) {
        return commands.stream().filter(c -> c.name().equals(commandName)).findFirst();
    }

    /** 명령 없이 상태만 보고하는 기능(예: Contact) */
    public boolean stateOnly() {
        return commands.isEmpty();
    }
}
