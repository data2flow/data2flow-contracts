package net.java21.data2flow.contracts;

import net.java21.data2flow.contracts.audit.AuditRecorder;
import net.java21.data2flow.contracts.authz.AccessGrant;
import net.java21.data2flow.contracts.authz.ContractsAuthorizationAutoConfiguration;
import net.java21.data2flow.contracts.authz.PermissionLookup;
import net.java21.data2flow.contracts.authz.RoleChecker;
import net.java21.data2flow.contracts.idempotency.IdempotencyFilter;
import net.java21.data2flow.contracts.idempotency.IdempotencyJdbcAutoConfiguration;
import net.java21.data2flow.contracts.idempotency.IdempotencyProperties;
import net.java21.data2flow.contracts.idempotency.IdempotencyStore;
import net.java21.data2flow.contracts.idempotency.IdempotencyWebAutoConfiguration;
import net.java21.data2flow.contracts.idempotency.JdbcIdempotencyStore;
import net.java21.data2flow.contracts.secret.SecretCipher;
import net.java21.data2flow.contracts.secret.SecretsAutoConfiguration;
import net.java21.data2flow.contracts.secret.SecretsProperties;
import net.java21.data2flow.contracts.web.ContractsWebAutoConfiguration;
import net.java21.data2flow.contracts.web.GatewayIdentityFilter;
import net.java21.data2flow.contracts.web.IdentityProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/** 자동 구성: 서블릿 서비스에만 켜지고(gateway 영향 없음), 서비스가 준 빈에 따라 RoleChecker·멱등·비밀값이 켜진다 */
class ContractsAutoConfigurationTest {

    private static final AutoConfigurations ALL = AutoConfigurations.of(ContractsWebAutoConfiguration.class,
            ContractsAuthorizationAutoConfiguration.class, IdempotencyJdbcAutoConfiguration.class,
            IdempotencyWebAutoConfiguration.class, SecretsAutoConfiguration.class);

    @Test
    @DisplayName("[IAM-04.01] PermissionLookup 빈이 있으면 RoleChecker가 생기고 AuditRecorder를 쓴다. 없으면 만들지 않는다")
    void roleChecker() {
        new WebApplicationContextRunner().withConfiguration(ALL)
                .withBean(PermissionLookup.class, () -> (o, u) -> AccessGrant.none())
                .withBean(AuditRecorder.class, () -> e -> { })
                .run(ctx -> assertThat(ctx).hasSingleBean(RoleChecker.class).hasBean("data2flowGatewayIdentityFilter"));
        new WebApplicationContextRunner().withConfiguration(ALL)
                .run(ctx -> assertThat(ctx).doesNotHaveBean(RoleChecker.class).doesNotHaveBean(IdempotencyFilter.class));
    }

    @Test
    @DisplayName("[OPS-12.03] data2flow.idempotency.jdbc-table을 주면 JDBC 저장소와 멱등 필터가 켜진다")
    void idempotency() {
        new WebApplicationContextRunner().withConfiguration(ALL)
                .withBean(JdbcTemplate.class, () -> new JdbcTemplate(new SimpleDriverDataSource()))
                .withPropertyValues("data2flow.idempotency.jdbc-table=data2flow_action.idempotency_keys",
                        "data2flow.idempotency.in-progress-timeout=30s")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(JdbcIdempotencyStore.class).hasBean("data2flowIdempotencyFilter");
                    IdempotencyProperties props = ctx.getBean(IdempotencyProperties.class);
                    assertThat(props.ttl()).isEqualTo(Duration.ofHours(24));
                    assertThat(props.inProgressTimeout()).isEqualTo(Duration.ofSeconds(30));
                    assertThat(ctx.getBean("data2flowIdempotencyFilter", FilterRegistrationBean.class).getFilter())
                            .isInstanceOf(IdempotencyFilter.class);
                });
        assertThat(IdempotencyProperties.defaults().maxBodyBytes()).isEqualTo(10 * 1024 * 1024);
    }

    @Test
    @DisplayName("[IAM-07.09] 신원 필터는 끌 수 있고, 선택 경로는 기본값에 더해진다")
    void identityFilterToggle() {
        new WebApplicationContextRunner().withConfiguration(ALL)
                .withPropertyValues("data2flow.identity.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean("data2flowGatewayIdentityFilter"));
        new WebApplicationContextRunner().withConfiguration(ALL)
                .withPropertyValues("data2flow.identity.optional-paths=/auth/login")
                .run(ctx -> {
                    assertThat(ctx.getBean(IdentityProperties.class).optionalPaths())
                            .containsAll(GatewayIdentityFilter.DEFAULT_OPTIONAL_PATHS).contains("/auth/login");
                });
    }

    @Test
    @DisplayName("[NFR-03.02] 마스터 키 설정이 있으면 SecretCipher가 생기고, 설정 값은 toString에 드러나지 않는다")
    void secrets() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(SecretsAutoConfiguration.class))
                .withPropertyValues("data2flow.secrets.master-keys=k1:" + key, "data2flow.secrets.active-key-id=k1")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(SecretCipher.class);
                    assertThat(ctx.getBean(SecretsProperties.class).toString()).doesNotContain(key);
                });
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(SecretsAutoConfiguration.class))
                .withPropertyValues("data2flow.secrets.master-keys=k1:short")
                .run(ctx -> assertThat(ctx).hasFailed());
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(SecretsAutoConfiguration.class))
                .run(ctx -> assertThat(ctx).doesNotHaveBean(SecretCipher.class));
    }

    @Test
    @DisplayName("리액티브 앱(gateway)에는 서블릿 필터·RoleChecker·멱등 처리를 만들지 않는다")
    void reactiveUnaffected() {
        new ReactiveWebApplicationContextRunner().withConfiguration(ALL)
                .withBean(PermissionLookup.class, () -> (o, u) -> AccessGrant.none())
                .withBean(IdempotencyStore.class, net.java21.data2flow.contracts.idempotency.InMemoryIdempotencyStore::new)
                .run(ctx -> assertThat(ctx).hasNotFailed()
                        .doesNotHaveBean(RoleChecker.class)
                        .doesNotHaveBean("data2flowGatewayIdentityFilter")
                        .doesNotHaveBean("data2flowIdempotencyFilter"));
    }
}
