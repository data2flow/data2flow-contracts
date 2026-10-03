package net.java21.data2flow.contracts.connector;

/**
 * 소스 하나의 연결 세션(connectors.md §1.1). 수명: {@code open → start → (pause ↔ resume)* → close}.
 *
 * <ul>
 *   <li>{@link #start()}: 연결·구독(폴링)을 시작한다. 연결은 백그라운드에서 맺고 상태는 {@link ConnectorContext#reportStatus}로 알린다.
 *       실패하면 백오프(1, 2, 4 … 최대 60초)로 다시 시도한다(DSC domain-model §3.2).</li>
 *   <li>{@link #pause()}: 새 메시지를 {@link RawSink}로 넘기지 않는다. 상대가 보관할 수 있는 프로토콜은 확인하지 않은 메시지를 다시 보내게 둔다.
 *       멈춘 동안 받은 메시지를 잃지 않는다.</li>
 *   <li>{@link #resume()}: 다시 넘긴다.</li>
 *   <li>{@link #close()}: 연결을 끊고 자원을 푼다. 기록 중인 메시지는 확인하지 않은 채 두어 상대가 다시 보내게 한다(무손실). 두 번 불러도 된다.</li>
 * </ul>
 */
public interface ConnectorSession extends AutoCloseable {

    void start();

    void pause();

    void resume();

    /** 지금 상태와 지표 */
    ConnectorStatus status();

    @Override
    void close();
}
