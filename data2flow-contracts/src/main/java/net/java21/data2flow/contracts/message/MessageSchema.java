package net.java21.data2flow.contracts.message;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 메시지 계약 record에 스키마 이름과 이 코드가 읽을 수 있는 최신 버전을 표시한다.
 *
 * <p>JSON Schema 파일은 {@code classpath:data2flow/contracts/schemas/{name}.v{version}.json}에 있다.
 * {@link MessageCodec}은 받은 메시지의 {@code v}가 1 이상 {@link #version()} 이하일 때만 읽는다.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MessageSchema {

    /** 스키마 이름(kebab-case), 예: {@code canonical-telemetry} */
    String name();

    /** 이 코드가 쓰는 버전이자 읽을 수 있는 최신 버전 */
    int version();
}
