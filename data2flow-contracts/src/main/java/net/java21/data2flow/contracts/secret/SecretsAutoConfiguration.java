package net.java21.data2flow.contracts.secret;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * {@code data2flow.secrets.master-keys}가 있으면 {@link SecretCipher}를 만든다(서블릿·리액티브 공통).
 * 비밀값을 저장하는 서비스(core, action, ingress)는 이 빈을 주입받으므로, 운영에서 키가 비어 있으면 기동이 실패한다.
 * 키 형식이 틀려도 기동이 실패한다.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "data2flow.secrets", name = "master-keys")
@EnableConfigurationProperties(SecretsProperties.class)
public class SecretsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SecretCipher data2flowSecretCipher(SecretsProperties properties) {
        return new SecretCipher(SecretKeyRing.parse(properties.masterKeys(), properties.activeKeyId()));
    }
}
