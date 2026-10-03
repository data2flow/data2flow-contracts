# data2flow-contracts

data2flow 서비스들이 함께 쓰는 계약 라이브러리입니다. 서비스를 만드는 백엔드 개발자가 읽습니다. 다 읽으면 서비스에 의존성을 넣고, 신원 필터·권한 검사(RoleChecker)·감사 기록·`Idempotency-Key`·`baseVersion`·호출 한도 응답·비밀값 암호화와 가림·ArchUnit 규칙을 연결할 수 있습니다.

규칙의 정본은 `data2flow-docs`의 `design/api-rules.md`(ADR-035), `design/auth.md` §7, `design/conventions.md` §3, `design/testing/backend.md` §6입니다. 이 문서는 그 규칙을 코드에서 어떻게 쓰는지만 설명합니다.

## 1. 모듈

| 모듈 | 서비스에서 쓰는 범위 | 내용 |
|---|---|---|
| `data2flow-bom` | `import` | 공통 의존성 버전 목록. 서비스는 이 BOM을 import하고 버전 없이 이름만 씁니다 |
| `data2flow-contracts` | `compile` | 공통 응답·오류·4개 언어 문구, 신원 헤더와 신원 필터, 권한표와 `RoleChecker`, 감사 기록 모양, `Idempotency-Key`, `baseVersion`, 목록 파라미터, 호출 한도 응답, 비밀값 암호화·가림, RabbitMQ 이름 상수 |
| `data2flow-contracts-test` | `test` | 테스트 키트: 공통 ArchUnit 규칙(조직 조건 강제, `Thread.sleep` 금지, 시스템 시계 직접 호출 금지) |

패키지(`net.java21.data2flow.contracts.*`)와 스펙의 대응은 아래와 같습니다.

| 패키지 | 스펙 | 주요 타입 |
|---|---|---|
| `web` | OPS-12.01·12.02 | `ApiResponse`, `ListApiResponse`, `CursorListApiResponse`, `PageParams`, `CursorParams`, `SortParams`, `GlobalExceptionHandler`, `GatewayIdentityFilter`, `RequestIdFilter` |
| `identity` | IAM-07.09 | `CurrentUser`, `CurrentUserHolder`, `DataflowHeaders` |
| `authz` | IAM-04.01·04.04·04.07 | `Permission`, `BuiltinRole`, `ApiScope`, `AccessGrant`, `SpaceScope`, `PermissionLookup`, `CachingPermissionLookup`, `RoleChecker` |
| `audit` | IAM-06.01 | `AuditEvent`, `AuditRecorder`, `AuditActorType`, `AuditResult`, `AuditCause`, `AuditActions` |
| `tenancy` | IAM-01.01·NFR-05.01 | `@OrganizationScopeExempt` |
| `idempotency` | OPS-12.03 | `@Idempotent`, `IdempotencyStore`, `JdbcIdempotencyStore`, `InMemoryIdempotencyStore` |
| `concurrency` | OPS-12.04 | `VersionCheck` |
| `ratelimit` | OPS-12.05 | `RateLimitInfo`, `RateLimitRejection`, `RateLimitHeaders` |
| `secret` | NFR-03.02 | `Secret`, `SecretCipher`, `SecretKeyRing`, `SecretMasker`, `SecretMaskingJsonMembersCustomizer`, `SecretMaskingMessageConverter` |
| `error` | OPS-12.01 | `ErrorCode`, `CommonErrorCode`(공통 코드 19개), `BusinessException` |

## 2. 빌드

```bash
./mvnw verify      # 단위·슬라이스(*Test) + 통합(*IT, Testcontainers PostgreSQL 18) + 커버리지 80% 검사
./mvnw install     # 로컬 저장소에 설치(다른 서비스 로컬 빌드용)
```

`*IT`는 Docker가 필요합니다. `main`에 병합되면 CI가 GitHub Packages에 배포합니다.

## 3. 서비스에 넣기

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>net.java21.data2flow</groupId>
      <artifactId>data2flow-bom</artifactId>
      <version>${data2flow-contracts.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
<dependencies>
  <dependency>
    <groupId>net.java21.data2flow</groupId>
    <artifactId>data2flow-contracts</artifactId>
  </dependency>
  <dependency>
    <groupId>net.java21.data2flow</groupId>
    <artifactId>data2flow-contracts-test</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

서블릿 서비스는 의존성만 넣으면 아래가 자동으로 켜집니다(리액티브 gateway에서는 하나도 켜지지 않습니다).

| 자동 구성 | 켜지는 조건 | 하는 일 |
|---|---|---|
| `ContractsWebAutoConfiguration` | 서블릿 앱 | 예외 처리기, Accept-Language(없으면 ko) 문구, `X-REQUEST-ID`, 신원 필터, JPA·JDBC 낙관적 잠금 충돌 → 409 |
| `ContractsAuthorizationAutoConfiguration` | 서블릿 앱 + `PermissionLookup` 빈 | `RoleChecker` 빈 |
| `IdempotencyJdbcAutoConfiguration` | `data2flow.idempotency.jdbc-table` + `JdbcTemplate` 빈 | `JdbcIdempotencyStore` |
| `IdempotencyWebAutoConfiguration` | 서블릿 앱 + `IdempotencyStore` 빈 | 멱등 필터와 인터셉터 |
| `SecretsAutoConfiguration` | `data2flow.secrets.master-keys` | `SecretCipher` 빈 |

`@WebMvcTest` 슬라이스에도 앞의 셋(웹, 권한, 멱등 웹)이 들어갑니다. 컨트롤러 테스트는 `X-USER-ID`·`X-ORG-ID` 헤더를 붙여 부릅니다.

## 4. 신원 필터 (IAM-07.09, auth.md §7)

`GatewayIdentityFilter`가 gateway가 넣은 `X-USER-ID`·`X-ORG-ID`(장기 토큰이면 `X-ACCESS-TOKEN-ID`·`X-TOKEN-SCOPE`)를 읽어 `CurrentUserHolder`에 담습니다.

- 신원이 필요한 경로에서 헤더가 없거나 숫자가 아니면 401 `AUTH_TOKEN_INVALID`(+ `WWW-Authenticate: Bearer error="invalid_token"`)입니다.
- 신원이 선택인 경로는 기본으로 `/internal/**`, `/actuator/**`, `/error`입니다. 헤더가 있으면 담고, 형식이 틀리면 401입니다.
- 공개 경로는 서비스 설정으로 더합니다. 예: auth의 로그인.

```yaml
data2flow:
  identity:
    optional-paths: /auth/login, /auth/password-reset/**
```

서비스 코드는 `CurrentUserHolder.get()`(없으면 예외) 또는 `CurrentUserHolder.find()`로 신원을 꺼냅니다. 메시지 소비자처럼 요청 밖에서 사용자를 대신하면 `CurrentUserHolder.callAs(user, () -> …)`를 씁니다.

## 5. 권한 검사: RoleChecker (IAM-04.01·04.04·04.05)

권한 이름은 `Permission`(스펙 "Permission 목록" 64개), 기본 역할은 `BuiltinRole`(ADMIN·INTEGRATOR·OPERATOR·ANALYST·VIEWER)이고 권한표를 그대로 옮겼습니다. 기기 수동 제어 `DEVICE_CONTROL`과 제어 노드를 포함한 플로우 적용 `FLOW_DEPLOY_CONTROL`은 별도 권한입니다(BR-IAM-17). `IAM_MANAGE`·`AUDIT_READ`는 `customRoleAllowed()=false`라 사용자 정의 역할에 넣을 수 없습니다(BR-IAM-30).

### 5.1 서비스가 할 일: `PermissionLookup` 빈 하나

```java
// core-api: 원천(DB)에서 판정. user_roles·custom_roles를 읽고 space_scope를 하위 공간까지 펼친다
@Bean
PermissionLookup permissionLookup(UserRoleQueryRepository roles) {
    return (organizationId, userId) -> roles.findActive(organizationId, userId)
            .map(r -> r.customPermissions() == null
                    ? AccessGrant.of(BuiltinRole.valueOf(r.role()), r.spaceScope())
                    : AccessGrant.custom(r.customPermissions(), r.spaceScope()))
            .orElse(AccessGrant.none());   // 없는 사용자·비활성·다른 조직 → 기본 거부
}

// 그 밖의 서비스: core 내부 API로 묻고 10초 이내로만 캐시한다(BR-IAM-13)
@Bean
PermissionLookup permissionLookup(CoreInternalClient core, Clock clock) {
    return new CachingPermissionLookup(core::findAccessGrant, Duration.ofSeconds(10), clock);
}
```

`AuditRecorder` 빈도 주면 403 거부가 감사 `ACCESS_DENIED`(result=DENIED, detail.permission)로 남습니다.

### 5.2 서비스 메서드 첫 줄에서 부르기

```java
public CommandResponse send(long deviceId, SendCommandRequest req) {
    long orgId = roleChecker.currentUser().organizationId();
    Device device = devices.findByIdAndOrganizationId(deviceId, orgId)          // 남의 조직 → 없음
            .orElseThrow(() -> new BusinessException(DeviceErrorCode.DEVICE_NOT_FOUND));
    roleChecker.require(Permission.DEVICE_CONTROL, device.getSpaceId(), DeviceErrorCode.DEVICE_NOT_FOUND);
    ...
}
```

| 상황 | 응답 |
|---|---|
| 다른 조직 자원, 공간 범위 밖 자원 | 404(넘긴 도메인 코드, 기본 `RESOURCE_NOT_FOUND`). 감사하지 않음 |
| 보이는 자원인데 역할 권한 없음 | 403 `PERMISSION_DENIED` + 감사 `ACCESS_DENIED` |
| 장기 토큰 요청인데 범위(`ApiScope`)가 그 권한을 열지 않음 | 403 `PERMISSION_DENIED` |
| 장기 토큰으로 플로우·스크립트 적용, 회원·토큰 관리 | `requireInteractive()` → 403 |

그 밖의 메서드: `requireSameOrganization`, `requireVisible`(조직·공간·권한 한 번에), `requireAdmin`(IAM_MANAGE), `has`, `spaceScope()`(목록·집계 쿼리에 `space_id IN (…)` 조건, IAM-04.06). 한 요청 안에서는 권한을 한 번만 조회합니다.

## 6. 감사 기록 (IAM-06.01)

`AuditEvent`는 `data2flow_core.audit_logs` 한 행이자 `POST /internal/core/audit-logs`(API-IAM-39) 본문입니다. 만들 때 `detail`의 비밀값(이름이 password·secret·token·apiKey… 로 끝나는 키 아래 전체, `Secret` 값, 문장 속 `password=…`)을 `***`로 가립니다(BR-IAM-21).

```java
auditRecorder.record(AuditEvent.builder(orgId, "SOURCE_UPDATED")
        .actor(CurrentUserHolder.get())            // 장기 토큰이면 detail.accessTokenId
        .target("SOURCE", Long.toString(sourceId))
        .detail("password", Map.of("before", oldPw, "after", newPw))   // → "***"
        .build());                                 // requestId는 MDC에서, 시각은 지금
```

자동 제어는 `.actor(AuditActorType.FLOW, flowId, flowName).cause(new AuditCause(flowId, version, nodeId, triggerMessageId))`로 원인을 남깁니다(IAM-06.04). `AuditRecorder` 구현은 core-api가 DB INSERT로, 다른 서비스는 API-IAM-39 호출로 둡니다.

## 7. Idempotency-Key (OPS-12.03, BR-OPS-20)

컨트롤러 메서드에 `@Idempotent`를 붙이면 켜집니다. 제어 명령처럼 키가 꼭 있어야 하면 `@Idempotent(required = true)`입니다.

| 경우 | 응답 |
|---|---|
| 같은 키·같은 요청(메서드·실제 경로·쿼리·본문)으로 24시간 안에 다시 옴 | 처음 응답(상태·`Location`·본문)을 그대로, 컨트롤러는 다시 실행하지 않음 |
| 같은 키·다른 요청(본문 또는 경로가 다름) | 409 `IDEMPOTENCY_KEY_REUSED` |
| 첫 요청이 아직 처리 중 | 409 `IDEMPOTENCY_CONFLICT` + `Retry-After: 2` |
| 처음 응답이 4xx·5xx·예외 | 저장하지 않고 키를 풀어 같은 키로 다시 시도 가능 |
| 키 없음(required), 64자 초과, 허용 문자(`A-Z a-z 0-9 . _ : -`) 밖 | 400 `INVALID_REQUEST`, `errors[0].field=Idempotency-Key` |

키 범위는 (조직, 사용자, 메서드 + 경로 템플릿)입니다. 결과 없이 60초(`in-progress-timeout`)가 지난 키는 처리하던 파드가 죽은 것으로 보고 넘겨받습니다. 저장은 업무 트랜잭션이 끝난 뒤에 하므로, 그 사이 파드가 죽으면 같은 키가 다시 실행될 수 있습니다. 장비 명령은 아웃박스 멱등 키(BR-ACT-02)로 한 번 더 막습니다.

### 7.1 저장소 연결

```yaml
data2flow:
  idempotency:
    jdbc-table: data2flow_action.idempotency_keys   # 자기 스키마의 표준 테이블
```

테이블은 각 서비스가 Flyway로 자기 스키마에 만듭니다(core는 `data2flow_core.idempotency_keys`가 이미 ERD에 있음). 모양은 design/erd/README.md §11.2와 같습니다.

```sql
CREATE TABLE data2flow_action.idempotency_keys (
    id              bigint GENERATED ALWAYS AS IDENTITY,
    organization_id bigint       NOT NULL,
    user_id         bigint       NOT NULL,
    route           varchar(200) NOT NULL,   -- 메서드 + 경로 템플릿
    idempotency_key varchar(64)  NOT NULL,
    request_hash    char(64)     NOT NULL,   -- 요청 SHA-256
    response_status integer,
    response_body   jsonb,                   -- {"contentType", "headers":{"Location"}, "body":"<본문>"}
    expires_at      timestamptz  NOT NULL,   -- 생성 + 24시간
    created_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_idempotency_keys PRIMARY KEY (id),
    CONSTRAINT uq_idempotency_keys_scope UNIQUE (organization_id, user_id, route, idempotency_key)
);
CREATE INDEX ix_idempotency_keys_expires_at ON data2flow_action.idempotency_keys (expires_at);
```

기한 지난 키는 서비스 스케줄러가 `idempotencyStore.deleteExpired(clock.instant())`로 지웁니다. 컨트롤러 슬라이스 테스트에서는 `InMemoryIdempotencyStore`를 빈으로 줍니다. 시계는 `Clock` 빈이 있으면 그것을 씁니다.

## 8. baseVersion (OPS-12.04, BR-OPS-21)

```java
VersionCheck.require(req.baseVersion(), entity.getVersion());              // 다르면 409 VERSION_CONFLICT
VersionCheck.require(req.baseVersion(), flow.getVersion(), FlowErrorCode.FLOW_VERSION_CONFLICT); // 도메인 코드
VersionCheck.requireUpdated(jdbc.update("… SET version = version + 1 WHERE id = ? AND version = ?", …));
long base = VersionCheck.baseVersion(patchBody);   // PATCH JsonNode, 없으면 400 errors[0].field=baseVersion
```

JPA `@Version`·Spring Data JDBC의 `OptimisticLockingFailureException`은 공통 처리기가 409 `VERSION_CONFLICT`로 바꿉니다. ETag·If-Match는 쓰지 않습니다.

## 9. 목록 파라미터 (OPS-12.02, BR-OPS-19)

| 타입 | 규칙 |
|---|---|
| `PageParams.of(page, size)` | page 1부터, size 기본 20·최대 100, 범위 밖은 경계값(400 아님). `offset()`, `keyword(raw)` |
| `CursorParams.of(cursor, size)` | 커서 목록 6종만. size 기본 50·최대 500, `fetchSize()`는 size+1 |
| `SortParams.parse(sort, allowed, defaults…)` | `sort=필드,asc|desc` 반복. 허용 밖은 400 `errors[0].field=sort`. `toOrderBy(필드→열)`은 표에 있는 열만 써서 SQL 주입이 없음 |

## 10. 호출 한도 응답 (OPS-12.05, BR-OPS-22)

`ratelimit` 패키지는 서블릿에 의존하지 않아 리액티브 gateway에서도 씁니다.

```java
// gateway 필터
RateLimitRejection r = RateLimitRejection.of(CommonErrorCode.RATE_LIMITED,
        new RateLimitInfo(limit, remaining, resetSeconds), errorMessages, locale);
exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(r.status()));   // 429
r.headers().forEach(exchange.getResponse().getHeaders()::set);             // Retry-After, X-RateLimit-*
// 본문 r.body() → {"header":{"isSuccessful":false,"resultCode":"RATE_LIMITED","resultMessage":"… 30초 후 …"}}

// 서블릿 서비스(예: auth 로그인 한도)
throw info.exceeded(CommonErrorCode.AUTH_RATE_LIMITED);
```

`X-RateLimit-Reset`은 한도가 다시 찰 때까지 남은 **초**입니다. 정상 응답에 남은 한도를 붙일 때는 `info.headers()`를 씁니다.

## 11. 비밀값 (NFR-03.02)

- **값 타입 `Secret`:** DTO의 비밀값 필드를 `Secret`으로 두면 JSON 응답·`toString()`·로그에 언제나 `***`가 나가고, 요청 본문의 문자열은 그대로 받습니다(쓰기 전용). 평문은 `reveal()`로만 꺼냅니다.
- **저장 암호화 `SecretCipher`:** AES-256-GCM. 암호문 앞에 버전과 키 ID(kid)가 붙어 키를 교체할 수 있습니다. `context`(예: `"data2flow_core.data_sources.secret:" + id`)를 함께 넣으면 다른 행·열로 옮긴 암호문은 풀리지 않습니다.

```yaml
data2flow:
  secrets:
    master-keys: ${DATA2FLOW_SECRETS_MASTER_KEYS}      # "k2026a:<Base64 32바이트>,k2027a:<…>"
    active-key-id: ${DATA2FLOW_SECRETS_ACTIVE_KEY_ID}  # 키가 하나면 생략 가능
```

```java
byte[] enc = secretCipher.encrypt(req.password(), "data2flow_core.data_sources.secret:" + id);   // bytea 열
Secret pw = secretCipher.decrypt(row.secretEnc(), "data2flow_core.data_sources.secret:" + id);
if (secretCipher.needsReencryption(enc)) { enc = secretCipher.reencrypt(enc, ctx); }   // 키 교체 배치
```

키 교체 절차(operations/security-operations.md #16): 새 키를 `master-keys`에 추가 → `active-key-id`를 새 키로 → 재암호화 배치 → 이전 키 제거. 키 값은 k8s Secret `data2flow-master-key`(로컬은 `.env`)에만 둡니다.

- **로그 가림:** JSON 로그(staging·prod)는 설정 한 줄로 모든 문자열 값을 가립니다.

```yaml
logging:
  structured:
    json:
      customizer: net.java21.data2flow.contracts.secret.SecretMaskingJsonMembersCustomizer
```

패턴 로그(로컬)는 `logback-spring.xml`에 `<conversionRule conversionWord="maskedMsg" class="net.java21.data2flow.contracts.secret.SecretMaskingMessageConverter"/>`를 두고 `%msg` 대신 `%maskedMsg`를 씁니다. 설정 내보내기·진단 묶음은 `SecretMasker.maskValue(map)`로 가립니다.

## 12. ArchUnit 규칙 (IAM-01.01·NFR-05.01, testing/backend.md §6)

```java
@AnalyzeClasses(packages = "net.java21.data2flow.core", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest static final ArchRule organizationScoped = Data2flowArchRules.REPOSITORY_QUERIES_ARE_ORGANIZATION_SCOPED;
    @ArchTest static final ArchRule noUnscopedCrud = Data2flowArchRules.UNSCOPED_CRUD_LOOKUPS_ARE_NOT_CALLED;
    @ArchTest static final ArchRule noSleep = Data2flowArchRules.NO_THREAD_SLEEP;
    @ArchTest static final ArchRule noSystemClock = Data2flowArchRules.NO_SYSTEM_CLOCK;
}
```

`archunit-junit5`는 서비스가 test 범위로 따로 넣습니다(버전은 BOM).

| 규칙 | 검사 |
|---|---|
| `REPOSITORY_QUERIES_ARE_ORGANIZATION_SCOPED` | 리포지토리(`@Repository`, Spring Data `Repository` 상속, `..repository..` 패키지의 `*Repository`)의 public 조회·수정·삭제 메서드(find·get·read·query·search·stream·count·exists·list·select·load·delete·remove·update·lock·page로 시작)는 `organizationId`·`orgId` 매개변수나, `organizationId()`·`organizationId` 필드를 가진 타입(`CurrentUser`, 검색 조건 record)의 매개변수를 받아야 합니다. save·insert처럼 엔티티를 받는 쓰기는 검사하지 않습니다 |
| `UNSCOPED_CRUD_LOOKUPS_ARE_NOT_CALLED` | 리포지토리 밖에서 Spring Data가 물려주는 `findById`·`findAll`·`existsById`·`deleteById`·`count` 등을 부르지 않습니다. `findByIdAndOrganizationId`를 씁니다 |
| `NO_THREAD_SLEEP` | `Thread.sleep` 호출 금지 |
| `NO_SYSTEM_CLOCK` | `Instant.now()`·`LocalDateTime.now()` 등 인자 없는 `now()`와 `System.currentTimeMillis()` 금지(`now(clock)`은 허용) |

조직 조건이 필요 없는 곳(조직 테이블 자체, 모든 조직을 도는 정리 배치, 토큰 해시로 찾는 인증 조회)은 메서드나 클래스에 `@OrganizationScopeExempt("이유")`를 붙입니다. 매개변수 이름을 읽으므로 서비스는 `-parameters`로 컴파일합니다(Spring Boot 부모 POM 기본값).

## 13. 설정 키 요약

| 키 | 기본값 | 설명 |
|---|---|---|
| `data2flow.identity.enabled` | `true` | 신원 필터 |
| `data2flow.identity.optional-paths` | (기본 `/internal/**`, `/actuator/**`, `/error`에 더함) | 신원 선택 경로 |
| `data2flow.idempotency.jdbc-table` | 없음 | 설정하면 `JdbcIdempotencyStore` |
| `data2flow.idempotency.ttl` | `24h` | 키 보관(BR-OPS-20) |
| `data2flow.idempotency.in-progress-timeout` | `60s` | 멈춘 키 넘겨받기 |
| `data2flow.idempotency.retry-after` | `2s` | 처리 중 409의 `Retry-After` |
| `data2flow.idempotency.max-body-bytes` | `10485760` | 미리 읽는 본문 상한(넘으면 413) |
| `data2flow.secrets.master-keys` | 없음 | `kid:Base64,…` |
| `data2flow.secrets.active-key-id` | 키가 하나면 그 키 | 암호화 키 |
