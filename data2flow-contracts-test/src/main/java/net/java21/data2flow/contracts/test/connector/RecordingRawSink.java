package net.java21.data2flow.contracts.test.connector;

import net.java21.data2flow.contracts.connector.RawSink;
import net.java21.data2flow.contracts.message.RawEnvelope;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 계약 테스트용 {@link RawSink}. 기록을 바로 끝내거나(기본), 붙잡아 두거나({@link #hold()}), 실패시킨다({@link #failNext(int)}).
 * "기록이 끝나기 전에는 상대에게 확인하지 않는다"(DSC-09.03, AT-DSC-18.1)를 확인하는 데 쓴다.
 */
public final class RecordingRawSink implements RawSink {

    private final List<RawEnvelope> written = new ArrayList<>();
    private final List<Pending> held = new ArrayList<>();
    private final AtomicInteger attempts = new AtomicInteger();
    private boolean holding;
    private int failuresLeft;

    private record Pending(RawEnvelope envelope, CompletableFuture<Void> future) {
    }

    @Override
    public CompletionStage<Void> write(RawEnvelope envelope) {
        attempts.incrementAndGet();
        synchronized (this) {
            if (failuresLeft > 0) {
                failuresLeft--;
                return CompletableFuture.failedFuture(new IllegalStateException("계약 테스트: 기록 실패(confirm 실패 흉내)"));
            }
            if (holding) {
                CompletableFuture<Void> future = new CompletableFuture<>();
                held.add(new Pending(envelope, future));
                return future;
            }
            written.add(envelope);
        }
        return CompletableFuture.completedFuture(null);
    }

    /** 이후 기록을 끝내지 않고 붙잡아 둔다(confirm이 늦게 오는 상황) */
    public synchronized void hold() {
        holding = true;
    }

    /** 붙잡아 둔 기록을 순서대로 끝내고, 이후 기록은 바로 끝낸다 */
    public void release() {
        List<Pending> toComplete;
        synchronized (this) {
            holding = false;
            toComplete = new ArrayList<>(held);
            held.clear();
            toComplete.forEach(p -> written.add(p.envelope()));
        }
        toComplete.forEach(p -> p.future().complete(null));
    }

    /** 다음 n번의 기록을 실패시킨다 */
    public synchronized void failNext(int n) {
        failuresLeft = n;
    }

    /** 기록이 끝난 봉투(성공 순서) */
    public synchronized List<RawEnvelope> written() {
        return List.copyOf(written);
    }

    public synchronized int writtenCount() {
        return written.size();
    }

    public synchronized int heldCount() {
        return held.size();
    }

    /** write 호출 수(성공·보류·실패 모두) */
    public int attempts() {
        return attempts.get();
    }
}
