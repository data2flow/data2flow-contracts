package net.java21.data2flow.contracts.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;

/**
 * 서블릿 서비스에 공통 응답 처리를 켠다: 예외 처리기, 4개 언어 오류 문구, Accept-Language 해석, 요청 ID.
 * 리액티브 gateway에서는 켜지지 않는다.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ContractsWebAutoConfiguration {

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
}
