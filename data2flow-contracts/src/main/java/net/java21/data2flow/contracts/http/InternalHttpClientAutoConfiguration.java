package net.java21.data2flow.contracts.http;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.http.client.JdkClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.autoconfigure.ClientHttpRequestFactoryBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import java.net.http.HttpClient;

/**
 * Boot가 만드는 {@code RestClient.Builder}·{@code RestTemplateBuilder}가 JDK HttpClient를 쓸 때 HTTP/1.1로 고정한다(ANA-04.01, ADR-059).
 * 주입받은 빌더로 내부 서비스를 부르는 곳(ingress·simulator 등)이 {@code Upgrade: h2c}를 보내지 않게 한다.
 * 다른 팩토리(Apache·Jetty·Reactor·Simple)는 원래 HTTP/1.1이라 손대지 않는다. {@code data2flow.http.force-http11=false}로 끈다.
 */
@AutoConfiguration(beforeName = "org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration")
@ConditionalOnClass({ClientHttpRequestFactoryBuilderCustomizer.class, JdkClientHttpRequestFactoryBuilder.class})
@ConditionalOnProperty(name = "data2flow.http.force-http11", havingValue = "true", matchIfMissing = true)
public class InternalHttpClientAutoConfiguration {

    @Bean
    public ClientHttpRequestFactoryBuilderCustomizer<JdkClientHttpRequestFactoryBuilder> data2flowJdkHttp11Customizer() {
        return new Http11Customizer();
    }

    /** 제네릭 타입을 남겨 JDK 빌더에만 적용되게 람다 대신 클래스로 둔다 */
    static final class Http11Customizer implements ClientHttpRequestFactoryBuilderCustomizer<JdkClientHttpRequestFactoryBuilder> {
        @Override
        public JdkClientHttpRequestFactoryBuilder customize(JdkClientHttpRequestFactoryBuilder builder) {
            return builder.withHttpClientCustomizer(b -> b.version(HttpClient.Version.HTTP_1_1));
        }
    }
}
