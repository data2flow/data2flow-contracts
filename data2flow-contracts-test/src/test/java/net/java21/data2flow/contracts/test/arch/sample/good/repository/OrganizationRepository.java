package net.java21.data2flow.contracts.test.arch.sample.good.repository;

import net.java21.data2flow.contracts.tenancy.OrganizationScopeExempt;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@OrganizationScopeExempt("조직 테이블 자체")
public class OrganizationRepository {

    public Optional<String> findCode(long id) {
        return Optional.empty();
    }
}
