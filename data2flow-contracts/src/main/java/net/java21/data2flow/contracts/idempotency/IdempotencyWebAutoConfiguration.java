package net.java21.data2flow.contracts.idempotency;

import net.java21.data2flow.contracts.web.ContractsWebAutoConfiguration;
import net.java21.data2flow.contracts.web.ServletErrorWriter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.util.List;

/**
 * {@link IdempotencyStore} 빈이 있으면 {@code @Idempotent} 처리를 켠다(OPS-12.03). 저장소는 서비스가 주거나
 * {@code data2flow.idempotency.jdbc-table}로 자동 구성한다. 시계는 {@link Clock} 빈이 있으면 그것을 쓴다.
 */
@AutoConfiguration(after = {IdempotencyJdbcAutoConfiguration.class, ContractsWebAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(IdempotencyStore.class)
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyWebAutoConfiguration {

    @Bean
    public FilterRegistrationBean<IdempotencyFilter> data2flowIdempotencyFilter(IdempotencyStore store,
                                                                               ServletErrorWriter errorWriter,
                                                                               IdempotencyProperties properties) {
        FilterRegistrationBean<IdempotencyFilter> bean =
                new FilterRegistrationBean<>(new IdempotencyFilter(store, errorWriter, properties.maxBodyBytes()));
        bean.setOrder(ContractsWebAutoConfiguration.IDENTITY_FILTER_ORDER + 10);
        bean.setUrlPatterns(List.of("/*"));
        return bean;
    }

    @Bean
    public WebMvcConfigurer data2flowIdempotencyWebMvcConfigurer(IdempotencyStore store, IdempotencyProperties properties,
                                                                 ObjectProvider<Clock> clock) {
        IdempotencyInterceptor interceptor =
                new IdempotencyInterceptor(store, properties, clock.getIfAvailable(Clock::systemUTC));
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor);
            }
        };
    }
}
