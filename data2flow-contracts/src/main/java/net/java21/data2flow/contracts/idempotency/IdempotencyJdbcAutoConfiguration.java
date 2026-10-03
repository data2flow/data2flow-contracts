package net.java21.data2flow.contracts.idempotency;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/** {@code data2flow.idempotency.jdbc-table}을 설정한 서비스에 PostgreSQL 멱등 저장소를 만든다 */
@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration")
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "data2flow.idempotency", name = "jdbc-table")
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyJdbcAutoConfiguration {

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(IdempotencyStore.class)
    public JdbcIdempotencyStore data2flowIdempotencyStore(JdbcTemplate jdbcTemplate, ObjectProvider<JsonMapper> jsonMapper,
                                                         IdempotencyProperties properties) {
        return new JdbcIdempotencyStore(jdbcTemplate, jsonMapper.getIfAvailable(() -> JsonMapper.builder().build()),
                properties.jdbcTable());
    }
}
