package net.java21.data2flow.contracts.test.arch.sample.good.repository;

import net.java21.data2flow.contracts.tenancy.OrganizationScopeExempt;
import net.java21.data2flow.contracts.test.arch.sample.Device;
import net.java21.data2flow.contracts.test.arch.sample.DeviceSearch;
import org.springframework.data.repository.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends Repository<Device, Long> {

    Optional<Device> findByIdAndOrganizationId(long id, long organizationId);

    List<Device> search(DeviceSearch search);

    long countByOrgId(long orgId);

    Device save(Device device);

    @OrganizationScopeExempt("보관 기간이 지난 행 정리 배치(모든 조직)")
    int deleteExpired(Instant before);
}
