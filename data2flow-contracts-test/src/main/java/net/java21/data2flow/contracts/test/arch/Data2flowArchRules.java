package net.java21.data2flow.contracts.test.arch;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import net.java21.data2flow.contracts.tenancy.OrganizationScopeExempt;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 모든 Java 서비스가 함께 쓰는 ArchUnit 규칙(design/testing/backend.md §6). 서비스는 테스트 하나로 실행한다.
 *
 * <pre>{@code
 * @AnalyzeClasses(packages = "net.java21.data2flow.core", importOptions = ImportOption.DoNotIncludeTests.class)
 * class ArchitectureTest {
 *     @ArchTest static final ArchRule organizationScoped = Data2flowArchRules.REPOSITORY_QUERIES_ARE_ORGANIZATION_SCOPED;
 *     @ArchTest static final ArchRule noUnscopedCrud = Data2flowArchRules.UNSCOPED_CRUD_LOOKUPS_ARE_NOT_CALLED;
 *     @ArchTest static final ArchRule noSleep = Data2flowArchRules.NO_THREAD_SLEEP;
 *     @ArchTest static final ArchRule noSystemClock = Data2flowArchRules.NO_SYSTEM_CLOCK;
 * }
 * }</pre>
 */
public final class Data2flowArchRules {

    /** 조회·수정·삭제로 보는 리포지토리 메서드 이름 접두어. save·insert처럼 엔티티(조직 ID 포함)를 받는 쓰기는 제외 */
    static final List<String> QUERY_PREFIXES = List.of(
            "find", "get", "read", "query", "search", "stream", "count", "exists", "list", "select", "load",
            "delete", "remove", "update", "lock", "page");
    static final Set<String> ORGANIZATION_PARAMETER_NAMES = Set.of("organizationId", "orgId");
    /** Spring Data가 물려주는, 조직 조건 없이 ID·전체로 찾는 메서드 */
    static final Set<String> UNSCOPED_CRUD_METHODS = Set.of(
            "findById", "findAll", "findAllById", "existsById", "getById", "getReferenceById", "count",
            "deleteById", "deleteAll", "deleteAllById", "deleteAllInBatch");
    private static final String SPRING_DATA_REPOSITORY = "org.springframework.data.repository.Repository";
    private static final String REPOSITORY_ANNOTATION = "org.springframework.stereotype.Repository";

    /**
     * IAM-01.01·NFR-05.01·BR-IAM-01: 리포지토리의 조회·수정·삭제 메서드는 조직 조건을 받아야 한다. 다음 중 하나면 통과한다.
     * <ul>
     *   <li>이름이 {@code organizationId} 또는 {@code orgId}인 매개변수(서비스는 {@code -parameters}로 컴파일한다)</li>
     *   <li>{@code organizationId()} 메서드나 {@code organizationId} 필드를 가진 타입의 매개변수(CurrentUser, 검색 조건 record)</li>
     *   <li>메서드나 클래스에 {@link OrganizationScopeExempt}(이유 필수)</li>
     * </ul>
     * 리포지토리는 {@code @Repository}가 붙었거나, Spring Data {@code Repository}를 상속했거나,
     * {@code ..repository..} 패키지에서 이름이 {@code Repository}로 끝나는 타입이다.
     */
    public static final ArchRule REPOSITORY_QUERIES_ARE_ORGANIZATION_SCOPED = methods()
            .that(repositoryQueryMethods())
            .should(takeOrganizationScope())
            .because("모든 업무 조회에는 organization_id 조건이 들어가야 한다(BR-IAM-01, IAM-01.01)")
            .allowEmptyShould(true);

    /**
     * BR-IAM-01 보조: 리포지토리 밖에서 Spring Data가 물려주는 {@code findById}·{@code findAll} 등을 부르지 않는다.
     * {@code findByIdAndOrganizationId}처럼 조직 조건이 있는 메서드를 쓴다.
     */
    public static final ArchRule UNSCOPED_CRUD_LOOKUPS_ARE_NOT_CALLED = noClasses()
            .that(isRepository().negate())
            .should().callMethodWhere(unscopedSpringDataCall())
            .because("ID만으로 찾으면 다른 조직의 자원이 보인다(BR-IAM-01). 조직 조건이 있는 리포지토리 메서드를 쓴다")
            .allowEmptyShould(true);

    /** design/testing: {@code Thread.sleep} 금지(테스트 포함). 대기는 Awaitility, 시간은 Clock */
    public static final ArchRule NO_THREAD_SLEEP = noClasses()
            .should().callMethodWhere(call(Thread.class, "sleep"))
            .because("Thread.sleep 대신 Awaitility·MutableClock을 쓴다(design/testing/README.md)")
            .allowEmptyShould(true);

    /** design/testing: 시스템 시계 직접 호출 금지. {@code Clock}을 주입받아 {@code Instant.now(clock)}을 쓴다 */
    public static final ArchRule NO_SYSTEM_CLOCK = noClasses()
            .should().callMethodWhere(noArgNow())
            .because("시간 로직은 Clock을 주입받아 테스트에서 MutableClock으로 바꾼다(design/testing/backend.md §3)")
            .allowEmptyShould(true);

    private Data2flowArchRules() {
    }

    static DescribedPredicate<JavaClass> isRepository() {
        return DescribedPredicate.describe("repositories", c ->
                c.isAnnotatedWith(REPOSITORY_ANNOTATION)
                        || (c.isInterface() && c.isAssignableTo(SPRING_DATA_REPOSITORY) && !c.getName().equals(SPRING_DATA_REPOSITORY))
                        || (c.getPackageName().contains(".repository") && c.getSimpleName().endsWith("Repository")));
    }

    static DescribedPredicate<JavaMethod> repositoryQueryMethods() {
        return DescribedPredicate.describe("repository query methods", m -> {
            JavaClass owner = m.getOwner();
            if (!isRepository().test(owner) || owner.isAnnotatedWith(OrganizationScopeExempt.class)
                    || m.isAnnotatedWith(OrganizationScopeExempt.class)) {
                return false;
            }
            if (m.getModifiers().contains(JavaModifier.STATIC) || m.getModifiers().contains(JavaModifier.SYNTHETIC)
                    || m.getModifiers().contains(JavaModifier.BRIDGE) || !m.getModifiers().contains(JavaModifier.PUBLIC)) {
                return false;
            }
            String name = m.getName();
            return QUERY_PREFIXES.stream().anyMatch(name::startsWith);
        });
    }

    static ArchCondition<JavaMethod> takeOrganizationScope() {
        return new ArchCondition<>("take an organization scope parameter") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean scopedByType = method.getRawParameterTypes().stream().anyMatch(Data2flowArchRules::carriesOrganizationId);
                boolean scoped = scopedByType || hasOrganizationParameterName(method);
                String message = method.getFullName() + " 에 organizationId 매개변수가 없습니다"
                        + " (조직 조건이 필요 없으면 @OrganizationScopeExempt(\"이유\"))";
                events.add(new SimpleConditionEvent(method, scoped, message));
            }
        };
    }

    static boolean carriesOrganizationId(JavaClass type) {
        return type.getAllMethods().stream()
                .anyMatch(m -> m.getName().equals("organizationId") && m.getRawParameterTypes().isEmpty())
                || type.getAllFields().stream().anyMatch(f -> f.getName().equals("organizationId"));
    }

    static boolean hasOrganizationParameterName(JavaMethod method) {
        Method reflected;
        try {
            reflected = method.reflect();
        } catch (RuntimeException | LinkageError ex) {
            return false;
        }
        for (Parameter parameter : reflected.getParameters()) {
            if (parameter.isNamePresent() && ORGANIZATION_PARAMETER_NAMES.contains(parameter.getName())) {
                return true;
            }
        }
        return false;
    }

    static DescribedPredicate<JavaMethodCall> unscopedSpringDataCall() {
        return DescribedPredicate.describe("Spring Data unscoped lookup", call ->
                UNSCOPED_CRUD_METHODS.contains(call.getName())
                        && call.getTargetOwner().isAssignableTo(SPRING_DATA_REPOSITORY)
                        && call.getTarget().resolveMember().map(member -> !member.isAnnotatedWith(OrganizationScopeExempt.class)).orElse(true));
    }

    static DescribedPredicate<JavaMethodCall> call(Class<?> owner, String name) {
        return DescribedPredicate.describe(owner.getSimpleName() + "." + name, c ->
                c.getTargetOwner().isEquivalentTo(owner) && c.getName().equals(name));
    }

    static DescribedPredicate<JavaMethodCall> noArgNow() {
        Set<String> clocks = Set.of("java.time.Instant", "java.time.LocalDateTime", "java.time.LocalDate", "java.time.LocalTime",
                "java.time.ZonedDateTime", "java.time.OffsetDateTime");
        return DescribedPredicate.describe("now() without Clock / System.currentTimeMillis()", c ->
                (clocks.contains(c.getTargetOwner().getName()) && c.getName().equals("now") && c.getTarget().getRawParameterTypes().isEmpty())
                        || (c.getTargetOwner().isEquivalentTo(System.class) && c.getName().equals("currentTimeMillis")));
    }
}
