package net.java21.data2flow.contracts.test.arch.sample.bad;

import net.java21.data2flow.contracts.test.arch.sample.Device;
import net.java21.data2flow.contracts.test.arch.sample.bad.repository.LeakyRepository;

import java.time.LocalDateTime;
import java.util.Optional;

/** 규칙 위반 예시. 실행하지 않고 바이트코드만 검사한다 */
public class LeakyService {

    private final LeakyRepository devices;

    public LeakyService(LeakyRepository devices) {
        this.devices = devices;
    }

    public Optional<Device> detail(long id) {
        return devices.findById(id);
    }

    public LocalDateTime now() {
        return LocalDateTime.now();
    }

    // NO_THREAD_SLEEP 규칙이 잡는지 보려는 위반 예시. 이 메서드는 어떤 테스트도 실행하지 않는다
    public void waitABit() throws InterruptedException {
        Thread.sleep(1);
    }
}
