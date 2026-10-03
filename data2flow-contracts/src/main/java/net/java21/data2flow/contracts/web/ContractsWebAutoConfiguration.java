package net.java21.data2flow.contracts.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 서블릿 서비스에 공통 응답 처리를 켠다: 예외 처리기, 4개 언어 오류 문구, Accept-Language 해석, 요청 ID,
 * gateway 신원 필터(X-USER-ID·X-ORG-ID → CurrentUserHolder), 낙관적 잠금 충돌 → 409.
 * 리액티브 gateway에서는 켜지지 않는다. Spring MVC 자동 구성보다 먼저 와서 {@code localeResolver}(Accept-Language가 없으면 ko)를
 * 이 라이브러리 것으로 정한다. 그러지 않으면 서버 JVM 기본 언어로 답한다.
 */
@AutoConfiguration(beforeName = "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(IdentityProperties.class)
public class ContractsWebAutoConfiguration {

    /** 요청 ID 다음, 멱등 필터 앞 */
    public static final int IDENTITY_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    @Bean
    @ConditionalOnMissingBean
    public ErrorMessages data2flowErrorMessages(ObjectProvider<MessageSource> messageSource) {
        return new ErrorMessages(messageSource.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler data2flowGlobalExceptionHandler(ErrorMessages messages) {
        return new GlobalExceptionHandler(messages);
    }

    @Bean
    @ConditionalOnMissingBean
    public ServletErrorWriter data2flowServletErrorWriter(ErrorMessages messages, ObjectProvider<JsonMapper> jsonMapper) {
        return new ServletErrorWriter(messages, jsonMapper.getIfAvailable(() -> JsonMapper.builder().build()));
    }

    @Bean(name = "localeResolver")
    @ConditionalOnMissingBean(name = "localeResolver")
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(ErrorMessages.SUPPORTED_LANGUAGES.stream().map(java.util.Locale::of).toList());
        resolver.setDefaultLocale(ErrorMessages.DEFAULT_LOCALE);
        return resolver;
    }

    @Bean
    public FilterRegistrationBean<RequestIdFilter> data2flowRequestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> bean = new FilterRegistrationBean<>(new RequestIdFilter());
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        bean.setUrlPatterns(List.of("/*"));
        return bean;
    }

    @Bean
    @ConditionalOnProperty(prefix = "data2flow.identity", name = "enabled", matchIfMissing = true)
    public FilterRegistrationBean<GatewayIdentityFilter> data2flowGatewayIdentityFilter(ServletErrorWriter errorWriter,
                                                                                        IdentityProperties properties) {
        FilterRegistrationBean<GatewayIdentityFilter> bean =
                new FilterRegistrationBean<>(new GatewayIdentityFilter(errorWriter, properties.optionalPaths()));
        bean.setOrder(IDENTITY_FILTER_ORDER);
        bean.setUrlPatterns(List.of("/*"));
        return bean;
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.dao.OptimisticLockingFailureException")
    static class OptimisticLockConfiguration {

        @Bean
        @ConditionalOnMissingBean
        OptimisticLockExceptionHandler data2flowOptimisticLockExceptionHandler(ErrorMessages messages) {
            return new OptimisticLockExceptionHandler(messages);
        }
    }
}
