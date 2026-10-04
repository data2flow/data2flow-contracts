package net.java21.data2flow.contracts.output;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/** 출력 본문 형식(output_connections.format) */
public enum OutputFormat {
    /** 표준 텔레메트리({@code CanonicalTelemetry} v1 JSON 그대로, 걸러진 측정값만) */
    CANONICAL,
    /** 안전한 템플릿(Mustache 문법, 로직 없음) */
    TEMPLATE,
    @JsonEnumDefaultValue
    UNKNOWN
}
