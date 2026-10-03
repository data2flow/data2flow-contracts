package net.java21.data2flow.contracts.authz;

import net.java21.data2flow.contracts.audit.AuditRecorder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.context.reactive.ConfigurableReactiveWebEnvironment;
import org.springframework.boot.web.context.reactive.ReactiveWebApplicationContext;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 서비스가 {@link PermissionLookup} 빈을 주면 {@link RoleChecker}를 만든다. {@link AuditRecorder} 빈이 있으면 거부를 감사에 남긴다.
 * 서블릿 서비스와 웹이 아닌 실행(k8s Job, 예: core-api 최초 관리자 {@code --spring.main.web-application-type=none})에서 켜고,
 * 리액티브 앱(gateway)에서는 켜지 않는다. Job 모드에서도 서비스 빈이 RoleChecker를 주입받으므로 없으면 기동이 실패한다.
 */
@AutoConfiguration
@Conditional(ContractsAuthorizationAutoConfiguration.NotReactive.class)
public class ContractsAuthorizationAutoConfiguration {

    /**
     * 리액티브 웹 앱이 아님(서블릿 웹 앱 또는 웹이 아닌 앱). {@code @ConditionalOnWebApplication(REACTIVE)}는 webflux 클래스가
     * 있을 때만 판정하므로 컨텍스트·환경 종류로 직접 본다
     */
    static class NotReactive implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !(context.getResourceLoader() instanceof ReactiveWebApplicationContext)
                    && !(context.getEnvironment() instanceof ConfigurableReactiveWebEnvironment);
        }
    }

    @Bean
    @ConditionalOnBean(PermissionLookup.class)
    @ConditionalOnMissingBean
    public RoleChecker data2flowRoleChecker(PermissionLookup lookup, ObjectProvider<AuditRecorder> auditRecorder) {
        return new RoleChecker(lookup, auditRecorder.getIfAvailable());
    }
}
