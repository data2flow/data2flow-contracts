package net.java21.data2flow.contracts.idempotency;

import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * PostgreSQL 멱등 저장소. 표준 테이블 모양(design/erd/README.md §11.2, core는 {@code data2flow_core.idempotency_keys})을 쓴다.
 * 테이블은 각 서비스가 자기 스키마에 Flyway로 만든다(DDL은 README). 원자성은 UNIQUE 제약과 조건부 UPDATE로 지킨다.
 *
 * <p>{@code response_body}(jsonb)에는 본문만이 아니라 재응답에 필요한 묶음
 * {@code {"contentType":…, "headers":{"Location":…}, "body":"<본문 문자열>"}}을 넣는다.
 */
public class JdbcIdempotencyStore implements IdempotencyStore {

    private static final Pattern TABLE = Pattern.compile("[a-z_][a-z0-9_]*(\\.[a-z_][a-z0-9_]*)?");
    private static final String WHERE_SCOPE = " WHERE organization_id = ? AND user_id = ? AND route = ? AND idempotency_key = ?";

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final String table;

    /**
     * @param table 스키마를 붙인 테이블 이름(예: {@code data2flow_action.idempotency_keys})
     */
    public JdbcIdempotencyStore(JdbcTemplate jdbc, JsonMapper json, String table) {
        if (!TABLE.matcher(table).matches()) {
            throw new IllegalArgumentException("테이블 이름 형식이 아닙니다: " + table);
        }
        this.jdbc = jdbc;
        this.json = json;
        this.table = table;
    }

    @Override
    public IdempotencyClaim claim(IdempotencyScope scope, String requestHash, Instant now, Instant expiresAt, Instant staleBefore) {
        for (int attempt = 0; attempt < 3; attempt++) {
            int inserted = jdbc.update("INSERT INTO " + table
                            + " (organization_id, user_id, route, idempotency_key, request_hash, expires_at, created_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?)"
                            + " ON CONFLICT (organization_id, user_id, route, idempotency_key) DO NOTHING",
                    scope.organizationId(), scope.userId(), scope.route(), scope.key(), requestHash, utc(expiresAt), utc(now));
            if (inserted == 1) {
                return IdempotencyClaim.acquired();
            }
            List<Row> rows = jdbc.query("SELECT request_hash, response_status, response_body::text, expires_at, created_at FROM "
                            + table + WHERE_SCOPE,
                    (rs, i) -> new Row(rs.getString(1), (Integer) rs.getObject(2), rs.getString(3),
                            rs.getObject(4, OffsetDateTime.class).toInstant(), rs.getObject(5, OffsetDateTime.class).toInstant()),
                    scope.organizationId(), scope.userId(), scope.route(), scope.key());
            if (rows.isEmpty()) {
                continue; // 그 사이 지워졌다. 다시 넣어 본다
            }
            Row row = rows.getFirst();
            if (!row.expiresAt().isAfter(now)) {
                jdbc.update("DELETE FROM " + table + WHERE_SCOPE + " AND expires_at <= ?",
                        scope.organizationId(), scope.userId(), scope.route(), scope.key(), utc(now));
                continue;
            }
            if (!row.requestHash().trim().equals(requestHash)) {
                return IdempotencyClaim.mismatch();
            }
            if (row.status() != null) {
                return IdempotencyClaim.replay(decode(row.status(), row.body()));
            }
            if (row.createdAt().isBefore(staleBefore)) {
                int taken = jdbc.update("UPDATE " + table + " SET created_at = ?" + WHERE_SCOPE
                                + " AND response_status IS NULL AND created_at < ?",
                        utc(now), scope.organizationId(), scope.userId(), scope.route(), scope.key(), utc(staleBefore));
                if (taken == 1) {
                    return IdempotencyClaim.acquired();
                }
            }
            return IdempotencyClaim.inProgress();
        }
        return IdempotencyClaim.inProgress();
    }

    @Override
    public void complete(IdempotencyScope scope, StoredResponse response) {
        jdbc.update("UPDATE " + table + " SET response_status = ?, response_body = CAST(? AS jsonb)" + WHERE_SCOPE,
                response.status(), encode(response), scope.organizationId(), scope.userId(), scope.route(), scope.key());
    }

    @Override
    public void release(IdempotencyScope scope) {
        jdbc.update("DELETE FROM " + table + WHERE_SCOPE + " AND response_status IS NULL",
                scope.organizationId(), scope.userId(), scope.route(), scope.key());
    }

    @Override
    public int deleteExpired(Instant now) {
        return jdbc.update("DELETE FROM " + table + " WHERE expires_at <= ?", utc(now));
    }

    private String encode(StoredResponse response) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("contentType", response.contentType());
        envelope.put("headers", response.headers());
        envelope.put("body", new String(response.body(), StandardCharsets.UTF_8));
        return json.writeValueAsString(envelope);
    }

    @SuppressWarnings("unchecked")
    private StoredResponse decode(int status, String body) {
        if (body == null) {
            return new StoredResponse(status, null, Map.of(), new byte[0]);
        }
        Map<String, Object> envelope = json.readValue(body, Map.class);
        Object text = envelope.get("body");
        return new StoredResponse(status, (String) envelope.get("contentType"),
                (Map<String, String>) envelope.getOrDefault("headers", Map.of()),
                text == null ? new byte[0] : text.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private record Row(String requestHash, Integer status, String body, Instant expiresAt, Instant createdAt) {
    }
}
