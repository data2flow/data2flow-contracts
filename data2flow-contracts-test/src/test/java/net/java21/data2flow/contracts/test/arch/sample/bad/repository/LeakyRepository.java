package net.java21.data2flow.contracts.test.arch.sample.bad.repository;

import net.java21.data2flow.contracts.test.arch.sample.Device;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface LeakyRepository extends CrudRepository<Device, Long> {

    Optional<Device> findByName(String name);
}
