package net.java21.data2flow.contracts.secret;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * 패턴 로그(로컬 콘솔)의 메시지에서 비밀값을 가리는 Logback 변환기. {@code logback-spring.xml}에서 {@code %msg} 대신 쓴다.
 *
 * <pre>{@code
 * <conversionRule conversionWord="maskedMsg"
 *                 class="net.java21.data2flow.contracts.secret.SecretMaskingMessageConverter"/>
 * }</pre>
 */
public class SecretMaskingMessageConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return SecretMasker.mask(super.convert(event));
    }
}
