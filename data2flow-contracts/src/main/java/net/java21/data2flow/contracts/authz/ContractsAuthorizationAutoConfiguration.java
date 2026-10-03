package net.java21.data2flow.contracts.authz;

import net.java21.data2flow.contracts.audit.AuditRecorder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * 서비스가 {@link PermissionLookup} 빈을 주면 {@link RoleChecker}를 만든다. {@link AuditRecorder} 빈이 있으면 거부를 감사에 남긴다.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ContractsAuthorizationAutoConfiguration {

    @Bean
    @ConditionalOnBean(PermissionLookup.class)
    @ConditionalOnMissingBean
    public RoleChecker data2flowRoleChecker(PermissionLookup lookup, ObjectProvider<AuditRecorder> auditRecorder) {
        return new RoleChecker(lookup, auditRecorder.getIfAvailable());
    }
}
