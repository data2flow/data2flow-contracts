package net.java21.data2flow.contracts.connector;

import java.util.Optional;

/**
 * 폴링 위치 저장소(DSC-09.09, BR-DSC-24·26). ingress 구현은 {@code data2flow_core.source_poll_cursors}에 쓰고, SINGLETON 커넥터의
 * 리스(connector_leases)를 확인해 <b>fencing token이 현재 리스보다 낮은 쓰기는 거부</b>한다({@link LeaseLostException}). 커넥터는 이
 * 예외를 받으면 즉시 수집을 멈춘다(리스를 다른 인스턴스가 넘겨받음).
 *
 * <p>계약: {@link #save}는 {@link RawSink#write}가 정상 완료된 레코드까지만 가리켜야 한다. 저장이 실패하면 커서는 이전 값이고, 다시
 * 읽은 레코드는 pipeline이 중복 키로 거른다.
 */
public interface PollCursorStore {

    /** 저장된 위치. 처음이면 빈 값 */
    Optional<PollCursor> load(long sourceId);

    /**
     * 위치를 저장한다.
     *
     * @throws LeaseLostException 리스를 잃었거나 fencing token이 낮다
     */
    void save(long sourceId, PollCursor cursor);

    /** 저장하지 않는 저장소(확인 방식이 CURSOR가 아닌 커넥터, 단위 테스트용) */
    static PollCursorStore none() {
        return NoopPollCursorStore.INSTANCE;
    }
}

/** 아무것도 저장하지 않는 저장소 */
enum NoopPollCursorStore implements PollCursorStore {
    INSTANCE;

    @Override
    public Optional<PollCursor> load(long sourceId) {
        return Optional.empty();
    }

    @Override
    public void save(long sourceId, PollCursor cursor) {
        // 저장하지 않는다
    }
}
