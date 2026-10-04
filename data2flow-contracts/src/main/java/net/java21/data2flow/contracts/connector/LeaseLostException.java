package net.java21.data2flow.contracts.connector;

/**
 * SINGLETON 커넥터가 리더 리스를 잃었다(BR-DSC-26: 리스 만료 60초 안에 다른 인스턴스가 넘겨받고, fencing token이 낮은 쪽의 기록은
 * 거부). 커넥터는 이 예외를 받으면 상대에게 확인하지 않고 즉시 수집을 멈춘다. 재시도하지 않는다.
 */
public class LeaseLostException extends RuntimeException {

    private final long sourceId;
    private final long fencingToken;

    public LeaseLostException(long sourceId, long fencingToken) {
        super("소스 " + sourceId + "의 리스를 잃었습니다(fencing token " + fencingToken + ")");
        this.sourceId = sourceId;
        this.fencingToken = fencingToken;
    }

    public long sourceId() {
        return sourceId;
    }

    /** 거부된 쓰기의 fencing token */
    public long fencingToken() {
        return fencingToken;
    }
}
