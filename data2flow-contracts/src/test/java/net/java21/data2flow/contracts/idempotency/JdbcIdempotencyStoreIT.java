package net.java21.data2flow.contracts.idempotency;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** OPS-12.03·BR-OPS-20: PostgreSQL 18 표준 테이블(design/erd/README.md §11.2)에서 멱등 키 판정이 원자적이다 */
@Testcontainers
class JdbcIdempotencyStoreIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    private static final String TABLE = "data2flow_test.idempotency_keys";
    private static final Instant T0 = Instant.parse("2026-10-03T00:00:00Z");
    private static final Instant EXPIRES = T0.plusSeconds(86_400);
    private static JdbcTemplate jdbc;
    private JdbcIdempotencyStore store;
    private final IdempotencyScope scope = new IdempotencyScope(1, 7, "POST /core/devices/{device-id}/commands", "k-1");

    @BeforeAll
    static void schema() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        // README의 DDL과 같다(data2flow_core.idempotency_keys, ddl/10-core-identity-ops.sql)
        jdbc.execute("CREATE SCHEMA data2flow_test");
        jdbc.execute("""
                CREATE TABLE data2flow_test.idempotency_keys (
                    id              bigint GENERATED ALWAYS AS IDENTITY,
                    organization_id bigint       NOT NULL,
                    user_id         bigint       NOT NULL,
                    route           varchar(200) NOT NULL,
                    idempotency_key varchar(64)  NOT NULL,
                    request_hash    char(64)     NOT NULL,
                    response_status integer,
                    response_body   jsonb,
                    expires_at      timestamptz  NOT NULL,
                    created_at      timestamptz  NOT NULL DEFAULT now(),
                    CONSTRAINT pk_idempotency_keys PRIMARY KEY (id),
                    CONSTRAINT uq_idempotency_keys_scope UNIQUE (organization_id, user_id, route, idempotency_key)
                )""");
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM " + TABLE);
        store = new JdbcIdempotencyStore(jdbc, JsonMapper.builder().build(), TABLE);
    }

    @Test
    @DisplayName("[OPS-12.03][AT-OPS-25.3] 잡기 → 완료 저장 → 같은 해시는 처음 응답(상태·Location·본문) 재생")
    void claimCompleteReplay() {
        assertThat(claim("a".repeat(64), T0).outcome()).isEqualTo(IdempotencyClaim.Outcome.ACQUIRED);
        assertThat(claim("a".repeat(64), T0).outcome()).isEqualTo(IdempotencyClaim.Outcome.IN_PROGRESS);
        StoredResponse response = new StoredResponse(201, "application/json",
                Map.of("Location", "/api/v1/core/commands/1"), "{\"response\":{\"commandId\":\"1\"}}".getBytes(StandardCharsets.UTF_8));
        store.complete(scope, response);

        IdempotencyClaim replay = claim("a".repeat(64), T0.plusSeconds(3600));
        assertThat(replay.outcome()).isEqualTo(IdempotencyClaim.Outcome.REPLAY);
        assertThat(replay.response()).isEqualTo(response);
        assertThat(jdbc.queryForObject("SELECT response_body->>'body' FROM " + TABLE, String.class)).contains("commandId");
    }

    @Test
    @DisplayName("[OPS-12.03][AT-OPS-25.4] 같은 키·다른 해시 → 불일치(409 IDEMPOTENCY_KEY_REUSED)")
    void mismatch() {
        claim("a".repeat(64), T0);
        assertThat(claim("b".repeat(64), T0).outcome()).isEqualTo(IdempotencyClaim.Outcome.MISMATCH);
    }

    @Test
    @DisplayName("[BR-OPS-20] 결과 없이 멈춘 키는 넘겨받고, 24시간 지난 키는 새로 잡고, 실패한 키는 풀린다")
    void staleExpiredReleased() {
        claim("a".repeat(64), T0);
        assertThat(store.claim(scope, "a".repeat(64), T0.plusSeconds(61), EXPIRES, T0.plusSeconds(1)).outcome())
                .isEqualTo(IdempotencyClaim.Outcome.ACQUIRED);

        store.complete(scope, new StoredResponse(204, null, Map.of(), new byte[0]));
        assertThat(claim("a".repeat(64), T0).response().body()).isEmpty();
        assertThat(store.claim(scope, "b".repeat(64), EXPIRES, EXPIRES.plusSeconds(86_400), EXPIRES).outcome())
                .isEqualTo(IdempotencyClaim.Outcome.ACQUIRED);

        store.release(scope);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + TABLE, Integer.class)).isZero();
        claim("a".repeat(64), T0);
        assertThat(store.deleteExpired(EXPIRES)).isEqualTo(1);
    }

    @Test
    @DisplayName("[OPS-12.03][AT-OPS-25.3] 같은 키로 동시에 8개가 잡으려 해도 하나만 ACQUIRED(실행 1회)")
    void concurrentClaims() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<IdempotencyClaim>> results = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Callable<IdempotencyClaim> task = () -> {
                start.await();
                return claim("a".repeat(64), T0);
            };
            results.add(pool.submit(task));
        }
        start.countDown();
        int acquired = 0;
        for (Future<IdempotencyClaim> f : results) {
            if (f.get().outcome() == IdempotencyClaim.Outcome.ACQUIRED) {
                acquired++;
            }
        }
        pool.shutdown();
        assertThat(acquired).isEqualTo(1);
    }

    @Test
    @DisplayName("테이블 이름은 스키마.테이블 형식만 받는다(SQL 주입 방지)")
    void tableName() {
        assertThatThrownBy(() -> new JdbcIdempotencyStore(jdbc, JsonMapper.builder().build(), "t; DROP TABLE x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private IdempotencyClaim claim(String hash, Instant now) {
        return store.claim(scope, hash, now, EXPIRES, now.minusSeconds(60));
    }
}
