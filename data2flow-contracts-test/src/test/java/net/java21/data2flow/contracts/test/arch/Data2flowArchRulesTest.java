package net.java21.data2flow.contracts.test.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** IAM-01.01·NFR-05.01(BR-IAM-01): 서비스가 함께 쓰는 ArchUnit 규칙이 위반을 잡고 올바른 코드는 통과시킨다 */
class Data2flowArchRulesTest {

    private static final String SAMPLE = "net.java21.data2flow.contracts.test.arch.sample";
    private final JavaClasses good = new ClassFileImporter().importPackages(SAMPLE + ".good");
    private final JavaClasses bad = new ClassFileImporter().importPackages(SAMPLE + ".bad", SAMPLE);

    @Test
    @DisplayName("[IAM-01.01][AT-IAM-11.2] organizationId 매개변수·조직 ID를 가진 타입·면제 어노테이션이 있는 리포지토리는 통과한다")
    void scopedRepositoriesPass() {
        // given: DeviceRepository(이름·타입·orgId·면제), OrganizationRepository(클래스 면제)
        // when & then
        assertThatCode(() -> Data2flowArchRules.REPOSITORY_QUERIES_ARE_ORGANIZATION_SCOPED.check(good)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("[IAM-01.01][NFR-05.01] 조직 조건 없는 조회 메서드는 Spring Data든 이름 규칙 리포지토리든 실패한다")
    void unscopedQueriesFail() {
        assertThatThrownBy(() -> Data2flowArchRules.REPOSITORY_QUERIES_ARE_ORGANIZATION_SCOPED.check(bad))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("LeakyRepository.findByName")
                .hasMessageContaining("JdbcLeakyRepository.listNames")
                .hasMessageContaining("@OrganizationScopeExempt");
    }

    @Test
    @DisplayName("[IAM-01.01][AT-IAM-11.2] 리포지토리 밖에서 findById 같은 조직 조건 없는 상속 메서드를 부르면 실패한다")
    void unscopedCrudCallsFail() {
        assertThatThrownBy(() -> Data2flowArchRules.UNSCOPED_CRUD_LOOKUPS_ARE_NOT_CALLED.check(bad))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("findById");
        assertThatCode(() -> Data2flowArchRules.UNSCOPED_CRUD_LOOKUPS_ARE_NOT_CALLED.check(good)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Thread.sleep과 Clock 없는 now() 호출을 잡는다(design/testing/backend.md §6)")
    void sleepAndSystemClock() {
        assertThatThrownBy(() -> Data2flowArchRules.NO_THREAD_SLEEP.check(bad)).hasMessageContaining("sleep");
        assertThatThrownBy(() -> Data2flowArchRules.NO_SYSTEM_CLOCK.check(bad)).hasMessageContaining("LocalDateTime.now");
        assertThatCode(() -> Data2flowArchRules.NO_THREAD_SLEEP.check(good)).doesNotThrowAnyException();
        assertThatCode(() -> Data2flowArchRules.NO_SYSTEM_CLOCK.check(good)).doesNotThrowAnyException();
    }
}
