package net.java21.data2flow.contracts.test.arch.sample.good;

import net.java21.data2flow.contracts.test.arch.sample.Device;
import net.java21.data2flow.contracts.test.arch.sample.good.repository.DeviceRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

public class DeviceService {

    private final DeviceRepository devices;
    private final Clock clock;

    public DeviceService(DeviceRepository devices, Clock clock) {
        this.devices = devices;
        this.clock = clock;
    }

    public Optional<Device> detail(long id, long organizationId) {
        return devices.findByIdAndOrganizationId(id, organizationId);
    }

    public Instant now() {
        return Instant.now(clock);
    }
}
