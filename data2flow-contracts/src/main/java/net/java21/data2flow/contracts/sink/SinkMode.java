package net.java21.data2flow.contracts.sink;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/** Sink 쓰기 모드(FLW-04.02 {@code sink.database} mode). UPSERT는 {@code upsertKeys}가 필요하다 */
public enum SinkMode {
    INSERT, UPSERT,
    @JsonEnumDefaultValue
    UNKNOWN
}
