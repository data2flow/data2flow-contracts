package net.java21.data2flow.contracts.capability;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 기능 상태 속성의 값 타입(ACT domain-model §2 capabilities.attributes: boolean|number|integer|enum|string).
 * 기능 정의 JSON은 JSON Schema 관례를 따라 소문자로 쓴다.
 */
public enum AttributeType {
    @JsonProperty("boolean") BOOLEAN,
    @JsonProperty("number") NUMBER,
    @JsonProperty("integer") INTEGER,
    @JsonProperty("enum") ENUM,
    @JsonProperty("string") STRING,
    /** 이 코드보다 새 정의가 쓴 타입. 검증은 형식만 통과시키지 않는다 */
    @JsonEnumDefaultValue @JsonProperty("unknown") UNKNOWN
}
