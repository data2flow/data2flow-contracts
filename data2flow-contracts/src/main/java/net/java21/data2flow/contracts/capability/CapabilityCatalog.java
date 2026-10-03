package net.java21.data2flow.contracts.capability;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 기능 정의 조회(표준 + 조직의 사용자 정의 {@code custom.*}). 표준은 {@link StandardCapabilities}, 사용자 정의는 core-api가 저장하고
 * 각 서비스가 캐시한다(ACT domain-model 머리말).
 */
public interface CapabilityCatalog {

    Optional<CapabilityDefinition> find(String name);

    List<CapabilityDefinition> all();

    /** 표준 기능만 */
    static CapabilityCatalog standard() {
        return of(List.of());
    }

    /**
     * 표준 + 사용자 정의. 사용자 정의 이름이 규칙(BR-ACT-22)에 맞지 않으면 {@link IllegalArgumentException}.
     */
    static CapabilityCatalog of(Collection<CapabilityDefinition> custom) {
        Map<String, CapabilityDefinition> byName = new LinkedHashMap<>();
        StandardCapabilities.all().forEach(d -> byName.put(d.name(), d));
        for (CapabilityDefinition d : custom) {
            StandardCapabilities.requireCustomName(d.name());
            if (d.standard()) {
                throw new IllegalArgumentException("사용자 정의 기능은 standard=false여야 합니다: " + d.name());
            }
            byName.put(d.name(), d);
        }
        List<CapabilityDefinition> list = List.copyOf(byName.values());
        return new CapabilityCatalog() {
            @Override
            public Optional<CapabilityDefinition> find(String name) {
                return Optional.ofNullable(byName.get(name));
            }

            @Override
            public List<CapabilityDefinition> all() {
                return list;
            }
        };
    }
}
