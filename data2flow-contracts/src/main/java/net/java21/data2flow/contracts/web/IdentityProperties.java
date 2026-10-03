package net.java21.data2flow.contracts.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 신원 필터 설정({@code data2flow.identity.*}).
 *
 * @param enabled       신원 필터를 켤지(기본 true)
 * @param optionalPaths 신원이 선택인 경로 패턴. 기본값({@code /internal/**}, {@code /actuator/**}, {@code /error})에 더해진다.
 *                      예: auth의 {@code /auth/login}, core의 공개 표시 화면 {@code /core/public/**}
 */
@ConfigurationProperties(prefix = "data2flow.identity")
public record IdentityProperties(Boolean enabled, List<String> optionalPaths) {

    public IdentityProperties {
        enabled = enabled == null || enabled;
        List<String> paths = new ArrayList<>(GatewayIdentityFilter.DEFAULT_OPTIONAL_PATHS);
        if (optionalPaths != null) {
            paths.addAll(optionalPaths);
        }
        optionalPaths = List.copyOf(paths);
    }
}
