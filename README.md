# data2flow-contracts

data2flow 서비스들이 함께 쓰는 계약 라이브러리입니다. 서비스를 만드는 백엔드 개발자가 읽습니다. 다 읽으면 서비스에 의존성을 넣고, 신원 필터·권한 검사(RoleChecker)·감사 기록·`Idempotency-Key`·`baseVersion`·호출 한도 응답·비밀값 암호화와 가림·ArchUnit 규칙을 연결하고, 수집 경로(M2)의 메시지 계약·디코더 SPI·커넥터 SPI와 계약 테스트 키트·메시지 추적 전파, 가상 폐루프(M3)의 기능(Capability) 카탈로그·명령 검증·상태 쌍·행동 요청·제어/시뮬레이터/플로우 이벤트를 쓸 수 있습니다.

규칙의 정본은 `data2flow-docs`의 `design/api-rules.md`(ADR-035), `design/auth.md` §7, `design/conventions.md` §3, `design/testing/backend.md` §6입니다. 이 문서는 그 규칙을 코드에서 어떻게 쓰는지만 설명합니다.

## 1. 모듈

| 모듈 | 서비스에서 쓰는 범위 | 내용 |
|---|---|---|
| `data2flow-bom` | `import` | 공통 의존성 버전 목록. 서비스는 이 BOM을 import하고 버전 없이 이름만 씁니다 |
| `data2flow-contracts` | `compile` | 공통 응답·오류·4개 언어 문구, 신원 헤더와 신원 필터, 권한표와 `RoleChecker`, 감사 기록 모양, `Idempotency-Key`, `baseVersion`, 목록 파라미터, 호출 한도 응답, 비밀값 암호화·가림, RabbitMQ 이름·라우팅 키·소비자 그룹, 메시지 계약(`RawEnvelope`·`CanonicalTelemetry`·`ConfigChangedMessage`·`DomainEvent`)과 JSON Schema, 디코더·커넥터 SPI, 메시지 추적 전파 |
| `data2flow-contracts-test` | `test` | 테스트 키트: 공통 ArchUnit 규칙(조직 조건 강제, `Thread.sleep` 금지, 시스템 시계 직접 호출 금지), 커넥터 계약 테스트 키트, 공유 메시지 픽스처 |

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
| `message` | ING-01.01·02.01·05.01·ACT-02.01 | `Message`, `@MessageSchema`, `MessageCodec`, `MessageSchemas`, `RawEnvelope`, `CanonicalTelemetry`, `ConfigChangedMessage`, `DomainEvent`, `EventType`, `SourceTypes`, `Quality`, `ActionRequest` |
| `message.event` | EVT-DEV·DSC·ING·TSD(M2) | `DeviceChanged`, `DeviceConnectivityChanged`, `DevicePendingCreated`, `SpaceChanged`, `GroupMembershipChanged`, `SourceRuntimeReported`, `SourceStatsReported`, `SourceConnectionChanged`, `SourceDataActivity`, `ConnectorCatalogReported`, `MetricUnverifiedRegistered`, `IngestAlert`, `IngestGapDetected`, `ClockSkewSuspected`, `AggregatesRecomputed`, `PartitionWarning` |
| `message.event` | EVT-ACT·SIM·FLW(M3) | `CommandStatusChanged`, `DeviceStateChanged`, `DeviceCommandAck`, `DeviceStateReported`, `SimRunChanged`, `SimFaultLabel`, `SimDataPurged`, `FlowApplyReported`, `FlowStateChanged` |
| `capability` | ACT-01.01~01.04·02.04·06.04 | `StandardCapabilities`, `CapabilityCatalog`, `CapabilityDefinition`, `CapabilityAttribute`, `CapabilityCommand`, `ExpectedEffect`, `AttributeConstraint`, `CommandArgsValidator`, `CommandValidation`, `ArgViolation`, `DeviceShadow`, `CapabilityStates`, `StateChange` |
| `command` | ACT-02.01~02.05·FLW-05.02 | `CommandSource`, `SourceType`, `CommandPriority`, `CommandStatus`, `CommandStatusReasons`, `CommandTarget`, `CommandPayload`, `ActionKind`, `ActionIdempotencyKeys` |
| `flow` | FLW-01.01·02(카탈로그) | `FlowDefinition`, `FlowNode`, `FlowNodeType` |
| `message.decoder` | ING-02.01 | `PayloadDecoder`, `DecodedUplink`, `DecodedValue`, `DecodeException`, `DecoderKeys` |
| `connector` | DSC-09.02 | `SourceConnector`, `ConnectorSession`, `RawSink`, `ConnectorDescriptor`, `ConnectorContext`, `SourceConfig`, `ConnectorStatus`, `ConnectionTestResult`, `ConnectorCatalogEntry`, `AckMode`, `ScalingMode` |
| `messaging` | ING-01.01·05.01·OPS-02.03·ACT-02.01 | `MessagingNames`, `SuperStreamSpec`, `QuorumQueueSpec`, `ConsumerGroups`, `StreamRoutingKeys`, `DedupKeys`, `ClientIds`, `MessageHeaders`, `MessageTracing` |

## 2. 빌드

```bash
./mvnw verify      # 단위·슬라이스(*Test) + 통합(*IT, Testcontainers PostgreSQL 18·RabbitMQ 3.13 Stream·Quorum) + 커버리지 80% 검사
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

- 웹 Access 토큰 요청에는 gateway가 로그인 세션 ID `X-SESSION-ID`(`DataflowHeaders.SESSION_ID`, introspection `sid`)도 넣습니다. 밖에서 들어온 같은 이름의 헤더는 항상 지워지므로 서비스는 이 값을 믿고 "이 기기" 표시 등에 씁니다(`CurrentUser`에는 담지 않습니다).
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

## 13. 메시지 계약 (ING-01.01·02.01·05.01, conventions.md §3)

RabbitMQ로 오가는 메시지는 모두 JSON이고 스키마 버전 `v`와 `messageId`(UUID)를 가집니다. 계약 record는 `message` 패키지에, JSON Schema(2020-12)는 `classpath:data2flow/contracts/schemas/`에 있습니다.

| 메시지 | 채널 | 스키마 파일 | 생산 → 소비 |
|---|---|---|---|
| `RawEnvelope` v1 (EVT-ING-01) | Super Stream `data2flow.raw` | `raw-envelope.v1.json` | ingress·simulator → pipeline |
| `CanonicalTelemetry` v1 (EVT-ING-02) | Super Stream `data2flow.telemetry` | `canonical-telemetry.v1.json` | pipeline → flow-engine·analytics·core-api(실시간) |
| `ConfigChangedMessage` v1 (EVT-DEV-04·DSC-01·SCR-01·ING-08) | fanout `data2flow.config` | `config-changed.v1.json` | core-api → 모든 서비스(받으면 DB에서 다시 읽음) |
| `DomainEvent<P>` v1 (EVT-DEV·DSC·ING·TSD·ACT·SIM·FLW) | topic `data2flow.events`, 라우팅 키 = `type` | `domain-event.v1.json` | 각 생산 서비스 → `{service}.events` |
| `ActionRequest` v1 (ACT-api §5.1, EVT-FLW-05) | direct `data2flow.actions`, 라우팅 키 = kind(`command`·`notify`·`sink`) | `action-request.v1.json` | flow-engine·core-api(아웃박스) → action(§18.4) |

```java
MessageCodec codec = MessageCodec.create();                 // 서비스에 하나, 스레드 안전
byte[] body = codec.write(telemetry);
CanonicalTelemetry t = codec.read(body, CanonicalTelemetry.class);
DomainEvent<?> e = codec.readEvent(body);                    // type(라우팅 키)으로 페이로드 타입 결정
DomainEvent<DevicePendingCreated> ev = DomainEvent.of(EventType.DEVICE_PENDING_CREATED, orgId, payload, requestId, clock);
```

- **호환 규칙:** 소비자는 모르는 필드를 무시하고, 모르는 enum 값은 기본값(`ConfigChangedMessage.EntityType.UNKNOWN`)으로 읽습니다. 필드 추가는 같은 버전에서 하고, 뜻이 바뀌는 변경만 `v`를 올립니다.
- **버전 검사:** 본문 `v`가 없거나 아는 버전보다 크면 `UnsupportedSchemaVersionException`, 형식 오류·필수 필드 누락이면 `MessageFormatException`입니다. 둘 다 재시도하지 말고 DLQ로 보냅니다.
- **도메인 이벤트 봉투:** `{v, messageId, type, organizationId, occurredAt, requestId?, payload}`. API 문서 이벤트 표의 "페이로드"가 `payload`입니다. 새 이벤트 종류는 `EventType`과 `domain-event.v1.json`의 `$defs`·`x-payloadTypes`에 함께 더합니다(계약 테스트가 둘이 맞는지 봅니다).
- **헤더:** `MessageHeaders.of(message)`가 `messageId`·`v`·`schema`·`organizationId`(이벤트는 `occurredAt`·`X-REQUEST-ID`도)를 만듭니다. 스트림은 애플리케이션 속성, 큐는 AMQP 헤더에 싣습니다.
- **계약 테스트:** `MessageSchemas.assertValid(message)`로 직렬화 결과가 스키마를 통과하는지 확인합니다(TC-ING-033). networknt json-schema-validator가 필요하며 테스트 키트가 가져옵니다.

### 13.1 스트림 이름·라우팅 키·소비자 그룹 (architecture.md §4.2, reliability-and-ha.md §2)

| 항목 | 값 | 코드 |
|---|---|---|
| Super Stream | `data2flow.raw` 12 파티션·7일/10GB, `data2flow.telemetry` 12 파티션·3일/5GB | `SuperStreamSpec.RAW`, `.TELEMETRY` |
| 라우팅 키 | raw: `sha1(sourceId + topic)` 16진수, telemetry: `deviceId` | `RawEnvelope.routingKey()`, `CanonicalTelemetry.routingKey()` |
| 소비자 그룹 | `pipeline`, `pipeline-reprocess`, `flow`, `analytics`, `core-live` | `ConsumerGroups.of(group, developer)` → 로컬은 `pipeline-nhn` |
| vhost | `data2flow`(prod), `data2flow-stg`, `data2flow-dev` | `MessagingNames.VHOST_*` |
| 중복 키 | ChirpStack `chirpstack:{deduplicationId}`, 그 밖 `sha256:…`, 값만 있는 payload `sha256b:…`(수신 시각 버킷) | `DedupKeys` |
| MQTT client-id | `{base}-{env}-{n}`, 개발자 `{base}-dev-{이름}-{n}`, 연결 테스트 `{base}-test-{난수}` | `ClientIds` |

같은 라우팅 키는 같은 파티션이고 한 파티션은 활성 소비자 하나가 순서대로 처리하므로 기기별 순서가 지켜집니다. 오프셋 저장은 DB 커밋 뒤에 합니다.

## 14. 디코더 SPI (ING-02.01)

pipeline의 디코더(`chirpstack-v4`·`generic-json`·`single-value`, DECODE 스크립트 `script:{id}@v{n}`)는 `PayloadDecoder`를 구현합니다. 원본 하나를 기기 식별 전 `DecodedUplink`(externalId, measuredAt?, 원본 키 측정값, link, tags) 하나로 바꾸고, 해석할 수 없으면 `DecodeException`(결과 코드 `ING_DECODE_FAILED`)을 던집니다. 문자열·불린 값(`magnet_status: "open"`)은 `DecodedValue`에 그대로 담아 상태 매핑 단계(ING-02.06)가 숫자로 바꿉니다.

## 15. 커넥터 SPI와 계약 테스트 키트 (DSC-09.02·09.03, connectors.md §1)

ingress 커넥터는 `SourceConnector`(설명·설정 스키마·연결 테스트·세션 열기)와 `ConnectorSession`(start·pause·resume·status·close)을 구현합니다. 받은 메시지는 `ConnectorContext.envelope(config, topic, payload)`로 `RawEnvelope`를 만들어 `RawSink.write`에 넘기고, **그 단계가 정상 완료된 뒤에만** 상대에게 확인(PUBACK·ack·오프셋 커밋·커서 저장)합니다.

카탈로그에 올리려면 커넥터마다 `AbstractConnectorContractTest`(테스트 키트)를 상속한 `*ContractIT`가 통과해야 합니다(BR-DSC-23).

```java
@Testcontainers
class Mqtt5ConnectorContractIT extends AbstractConnectorContractTest {
    @Container static final GenericContainer<?> EMQX = new GenericContainer<>("emqx/emqx:5").withExposedPorts(1883);
    @Override protected SourceConnector connector() { return new MqttConnector(); }
    @Override protected SourceConfig sourceConfig() { return new SourceConfig(1, 3, SourceTypes.CONNECTOR, "mqtt", config(), secrets(), "kit-0"); }
    @Override protected ContractPeer peer() { return mqttPeer; }   // 발행하고 확인(PUBACK) 수를 센다
}
```

키트 시나리오: 설명·스키마·카탈로그 항목, 연결 테스트 성공, 1,000건 무손실 수신과 봉투 필드, **기록 전 확인 금지**(AT-DSC-18.1), 기록 실패 시 재전송, 일시정지·재개, 상태 보고와 닫기. 확인 방식이 `AUTO`·`NONE`인 커넥터는 확인 시점 시나리오를 건너뜁니다(화면에 "유실 가능").

## 16. 공유 메시지 픽스처 (TC-ING-033·065)

`MessageFixtures`(테스트 키트)는 아카데미 실측 6종(EM300-TH, EM320-TH, EM500-CO2, AM103, AM107, WS302)의 `CanonicalTelemetry`, 가상·승인 대기·늦은 도착·파생 항목 메시지, 모르는 필드가 섞인 메시지(`with-unknown-fields`), ChirpStack v4 업링크 원본을 줍니다. pipeline은 디코더 출력이, 소비 서비스는 역직렬화가 이 픽스처와 맞는지 계약 테스트에서 확인합니다.

## 17. 메시지 추적 전파 (OPS-02.03)

`MessageTracing`이 W3C `traceparent`를 메시지 헤더에 넣고 꺼내 ingress → pipeline → flow-engine을 하나의 추적으로 잇습니다. Micrometer Tracing을 쓰므로 OTel 브리지가 있으면 OTel로 내보내고, 추적을 끈 서비스는 `MessageTracing.noop()`을 씁니다(동작은 같음).

```java
Map<String, Object> headers = MessageHeaders.of(envelope);
Span span = tracing.startProducerSpan(MessagingNames.STREAM_RAW, headers);      // 발행 전
producer.send(message(headers), status -> tracing.end(span, status.isConfirmed() ? null : failure));

Span span = tracing.startConsumerSpan(MessagingNames.STREAM_RAW, applicationProperties);   // 소비
try (Tracer.SpanInScope scope = tracing.inScope(span)) { handle(message); } finally { span.end(); }
```

`ObservabilityIT`가 실제 RabbitMQ 3.13 Super Stream에서 같은 기기의 파티션·순서 유지, 헤더, 추적 연결을 확인합니다.

> 사용자 JS 샌드박스(GraalJS, ADR-008)는 이 라이브러리에 없습니다. 스펙 배치(SCR-02.01·02.02)가 data2flow-pipeline이므로 pipeline이 만듭니다. GraalJS 버전(`polyglot`·`js-community`)만 BOM이 정합니다.

## 18. 제어 계약: 기능·명령·상태 쌍 (M3, ACT-01·02, ADR-009)

화면·플로우·AI 어디서 제어하든 명령은 "기기 + 기능(Capability) + 명령 + 인자" 한 모양이고 action의 제어 창구 하나를 거칩니다. 이 라이브러리는 그 모양과 검증만 주고, 권한·인터락·보호·드라이버 호출은 action이 합니다.

### 18.1 기능 카탈로그 (ACT-01.01·01.02·01.04)

표준 기능 7종의 정본은 JSON 파일 `classpath:data2flow/contracts/capabilities/{이름}.json`(웹·AI도 같은 파일)이고 형식은 `capability-definition.v1.json`입니다. 명령은 모두 목표 상태 설정 `set`입니다(BR-ACT-03).

| 기능 | 속성 | `set` 인자 | Matter |
|---|---|---|---|
| Switch | on: boolean | `{on}` 필수 | OnOff |
| Thermostat | mode: off·cool·heat·dry·fan·auto, targetTemperature: 5~35 °C·0.5 간격, currentTemperature(읽기 전용) | `{mode?, targetTemperature?}` 1개 이상 | Thermostat |
| FanSpeed | level: 0 이상 정수(최댓값은 모델 제약), auto: boolean | `{level?, auto?}` 1개 이상 | FanControl |
| Ventilation | mode: off·on·auto, level: 1~3 | `{mode?, level?}` 1개 이상 | FanControl |
| Dimmer | level: 0~100 % | `{level}` 필수 | LevelControl |
| Lock | locked: boolean, battery %(읽기 전용) | `{locked}` 필수 | DoorLock |
| Contact | open: boolean(읽기 전용) | 명령 없음 | BooleanState |

```java
CapabilityCatalog catalog = CapabilityCatalog.of(customDefinitions);   // 표준 + 조직의 custom.*(BR-ACT-22 이름 검사)
StandardCapabilities.requireCustomName("custom.Humidifier");           // 표준 이름이면 예외 → CAPABILITY_NAME_RESERVED
```

### 18.2 명령 검증 (BR-ACT-01: 기능 스키마 → 모델 제약 → 조직 절대 한계)

```java
CommandValidation r = CommandArgsValidator.validate(catalog, "Thermostat", "set", Map.of("targetTemperature", 31),
        modelConstraints,   // {targetTemperature: AttributeConstraint.range(18, 30)} (ACT-01.03)
        absoluteLimits);    // {targetTemperature: AttributeConstraint.range(18, 28)} (ACT-06.04)
if (!r.ok()) throw new BusinessException(ActionErrorCode.valueOf(r.resultCode().get()), r.fieldErrors());
// r.violations().get(0): field=args.targetTemperature, reason=MODEL_CONSTRAINT, min=18, max=30
```

| 위반(`ArgViolation.Reason`) | `resultCode()` |
|---|---|
| CAPABILITY_NOT_SUPPORTED, COMMAND_NOT_SUPPORTED | `CAPABILITY_NOT_SUPPORTED` |
| UNKNOWN_ARG(읽기 전용 포함)·MISSING_ARG·TOO_FEW_ARGS·WRONG_TYPE·NOT_ALLOWED_VALUE·OUT_OF_STANDARD_RANGE·STEP_MISMATCH | `COMMAND_ARGS_INVALID` |
| MODEL_CONSTRAINT | `COMMAND_ARG_OUT_OF_RANGE` |
| ABSOLUTE_LIMIT | `COMMAND_ABSOLUTE_LIMIT` |

앞 단계에서 걸리면 뒤 단계는 보지 않습니다. 검증기는 외부 라이브러리 없이 동작하고, 명령 인자 JSON Schema와 판정이 같은지 계약 테스트가 확인합니다. 조직 한계 설정 검사(LIMIT_WIDER_THAN_MODEL)는 `limit.within(model)`, 화면 컨트롤 범위(API-ACT-03 effectiveConstraints)는 `model.intersect(limit)`입니다.

### 18.3 상태 쌍 (ACT-02.04, BR-ACT-04·05)

```java
DeviceShadow s = shadow.withDesired("Thermostat", args);                  // desiredVersion + 1
Optional<DeviceShadow> next = s.withReported(version, capabilities, at);   // 버전이 크지 않으면 빈 값(버림)
next.get().isApplied("Thermostat", args);   // APPLIED 판정(24 == 24.0)
s.noChange("Switch", Map.of("on", true));   // SKIPPED(NO_CHANGE)
CapabilityStates.changes(before, after);    // EVT-ACT-02 changed[]
```

### 18.4 행동 요청과 큐 (ACT-api §5.1, EVT-FLW-05, ADR-020)

`ActionRequest` v1(`action-request.v1.json`)은 direct exchange `data2flow.actions`로 갑니다.

| kind | 라우팅 키 | Quorum 큐(`QuorumQueueSpec`) |
|---|---|---|
| COMMAND, SCENE | `command` | `action.commands`(prefetch 20) |
| NOTIFY | `notify` | `action.notifications` |
| SINK | `sink` | `action.sinks` |

모든 큐는 `x-delivery-limit=5`, DLX `data2flow.dlx`(라우팅 키 = 큐 이름) → `{queue}.dlq`이고 선언 인자는 `spec.arguments()`입니다. 서비스 이벤트 큐는 `QuorumQueueSpec.events("action")` → `action.events`.

```java
// flow-engine 제어 노드 → 아웃박스
String key = ActionIdempotencyKeys.flow(flowId, nodeId, triggerMessageId);   // sha256, 버전 제외(BR-FLW-13)
ActionRequest req = ActionRequest.command(orgId, key, CommandSource.flow(flowId, version, nodeId, triggerMessageId),
        validUntil, new CommandPayload(CommandTarget.space(31, "controls", "Thermostat", false), "Thermostat", "set",
        Map.of("mode", "cool", "targetTemperature", 24), true), clock);      // priority = AUTO(출처가 정함, BR-ACT-24)
outbox.insert(req.idempotencyKey(), req.routingKey(), codec.write(req));

// action 소비
ActionRequest req = codec.read(body, ActionRequest.class);
CommandPayload cmd = req.commandPayload();
```

우선순위는 `CommandPriority.forSource`(USER·BULK=MANUAL, SYSTEM=SAFETY, SCHEDULE, FLOW·RULE=AUTO, AI)로만 정하고 장면은 `forScene(실행 출처)`입니다. `CommandSource.requireComplete()`는 출처별 필수 칸(AI는 승인자)을 확인합니다(BR-ACT-15).

출처 공간 `source.spaceId`(선택, `CommandSource.flow(…, spaceId)`·`withSpaceId`)는 플로우·규칙이 판단한 공간(트리거·대상 공간)입니다. action은 기기 대상(`target.deviceId`) 명령에서도 이 값으로 샌드박스 판정(BR-ACT-23)을 하고, 없으면 `target.spaceId`를 씁니다. 설정 변경 `ConfigChangedMessage.EntityType`에는 `CAPABILITY`(id = 사용자 정의 기능 이름)와 `DRIVER`(id = 드라이버 ID)가 있어 action이 그 기능·드라이버를 쓰는 제어 프로필만 지웁니다(ADR-043 열린 요청 ①②).

### 18.5 M3 도메인 이벤트 (`data2flow.events`)

| 종류(`EventType`) | 라우팅 키 | 페이로드 | 생산 → 소비 |
|---|---|---|---|
| `commandStatus(status)` EVT-ACT-01 | `command.status.{소문자}`(예: `command.status.applied`, `command.status.queued_for_downlink`) | `CommandStatusChanged` | action → core·flow·ai |
| DEVICE_STATE_CHANGED EVT-ACT-02 | `device.state.changed` | `DeviceStateChanged` | action → flow·core·simulator |
| DEVICE_COMMAND_ACK EVT-ACT-06 = EVT-SIM-03 | `device.command.ack` | `DeviceCommandAck` | simulator·드라이버 어댑터 → action |
| DEVICE_STATE_REPORTED EVT-ACT-07 = EVT-SIM-03 | `device.state.reported` | `DeviceStateReported` | simulator·드라이버 어댑터 → action |
| `simRun(event)` EVT-SIM-01 | `sim.run.{started\|paused\|resumed\|stopped\|completed\|failed\|throttled\|reset}` | `SimRunChanged` | simulator → core·analytics |
| SIM_FAULT_STARTED·ENDED EVT-SIM-02 | `sim.fault.{started\|ended}` | `SimFaultLabel` | simulator → analytics·core |
| SIM_DATA_PURGED EVT-SIM-04 | `sim.data.purged` | `SimDataPurged` | core → core·analytics |
| FLOW_APPLY_REPORTED EVT-FLW-02 | `flow.apply.reported` | `FlowApplyReported` | flow-engine → core |
| FLOW_STATE_CHANGED EVT-FLW-03 | `flow.state.changed` | `FlowStateChanged` | flow-engine → core·action |

### 18.6 플로우 정의와 노드 카탈로그 (FLW-api §5, API-FLW-30)

`FlowDefinition`(`flow-definition.v1.json`, 표시 `data2flow.flow-definition/v1`)은 core-api 저장 검증·flow-engine 컴파일·웹 캔버스가 같이 씁니다. 노드 `config`는 종류별 `FlowNodeType.configSchema`(`flow-node-type.v1.json`)가 정하고, 노드 ID 중복·없는 노드 연결은 `structuralErrors()`가 봅니다.

### 18.7 공유 픽스처 (TC-ACT-027, TC-SIM-036)

`MessageFixtures.actionRequest("flow-command-heatwave")`(플로우 "고온이면 냉방" 제어 노드), `"user-command-device"`(모르는 필드 포함), `MessageFixtures.domainEvent("device-command-ack-virtual")` 등 M3 이벤트 5종. flow-engine 생산자와 action 소비자가 같은 파일로 계약 테스트를 합니다.

> 드라이버 SPI(`DeviceDriver`)와 드라이버 계약 테스트 키트는 SPI 패키지가 action(`net.java21.data2flow.action.actuation.driver`)에 있으므로 action이 둡니다(ACT-api §5.3).

## 19. 설정 키 요약

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
