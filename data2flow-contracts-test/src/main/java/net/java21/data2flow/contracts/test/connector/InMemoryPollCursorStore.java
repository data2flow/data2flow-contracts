package net.java21.data2flow.contracts.test.connector;

import net.java21.data2flow.contracts.connector.LeaseLostException;
import net.java21.data2flow.contracts.connector.PollCursor;
import net.java21.data2flow.contracts.connector.PollCursorStore;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 계약 테스트용 폴링 위치 저장소(DSC-09.09). 저장 횟수를 세고, {@link #revokeLease()} 뒤의 저장은 {@link LeaseLostException}으로 거부해
 * 리스를 잃은 인스턴스의 쓰기가 막히는지(BR-DSC-26) 시험할 수 있다. 같은 객체를 두 세션에 주면 "재시작 후 이어 읽기"를 흉내 낸다.
 */
public final class InMemoryPollCursorStore implements PollCursorStore {

    private final Map<Long, PollCursor> cursors = new ConcurrentHashMap<>();
    private final AtomicInteger saves = new AtomicInteger();
    private volatile boolean leaseRevoked;

    @Override
    public Optional<PollCursor> load(long sourceId) {
        return Optional.ofNullable(cursors.get(sourceId));
    }

    @Override
    public void save(long sourceId, PollCursor cursor) {
        if (leaseRevoked) {
            throw new LeaseLostException(sourceId, 1);
        }
        cursors.put(sourceId, cursor);
        saves.incrementAndGet();
    }

    /** 저장 횟수 */
    public int saves() {
        return saves.get();
    }

    /** 이후 저장을 리스 상실로 거부한다 */
    public void revokeLease() {
        leaseRevoked = true;
    }
}
