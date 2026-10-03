package net.java21.data2flow.contracts.capability;

import com.fasterxml.jackson.annotation.JsonIgnore;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 기능의 명령 하나. 명령은 토글이 아니라 <b>목표 상태 설정</b>이다(BR-ACT-03): {@code sets}에 적힌 속성을 인자 값으로 맞춘다.
 *
 * @param name 명령 이름(표준 기능은 모두 {@code set})
 * @param sets 이 명령이 설정하는 속성 이름(인자 이름과 같다)
 * @param args 인자 JSON Schema(2020-12). {@code properties}의 키는 {@code sets}와 같고 각 값은 속성 범위를 그대로 옮긴 것이다.
 *             {@code required}·{@code minProperties}를 검증에 쓴다
 */
public record CapabilityCommand(String name, List<String> sets, JsonNode args) {

    public CapabilityCommand {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("명령 이름은 필수입니다");
        }
        sets = sets == null ? List.of() : List.copyOf(sets);
    }

    /** 반드시 있어야 하는 인자(args.required) */
    @JsonIgnore
    public List<String> requiredArgs() {
        List<String> required = new ArrayList<>();
        if (args != null && args.get("required") != null) {
            args.get("required").forEach(n -> required.add(n.asString()));
        }
        return required;
    }

    /** 최소 인자 개수(args.minProperties, 없으면 0) */
    @JsonIgnore
    public int minArgs() {
        return args != null && args.get("minProperties") != null ? args.get("minProperties").asInt() : 0;
    }
}
