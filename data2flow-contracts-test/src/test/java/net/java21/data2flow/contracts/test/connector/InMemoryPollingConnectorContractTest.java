package net.java21.data2flow.contracts.test.connector;

import net.java21.data2flow.contracts.connector.PollCursorStore;
import net.java21.data2flow.contracts.connector.SourceConfig;
import net.java21.data2flow.contracts.connector.SourceConnector;
import net.java21.data2flow.contracts.message.SourceTypes;
import tools.jackson.databind.json.JsonMapper;

/** DSC-09.09 TC-DSC-290: 올바른 폴링(CURSOR) 커넥터는 키트의 모든 시나리오(재시작 후 이어 읽기 포함)를 통과한다 */
class InMemoryPollingConnectorContractTest extends AbstractConnectorContractTest {

    private final InMemoryPollCursorStore store = new InMemoryPollCursorStore();
    private final InMemoryPollingConnector.Server server = new InMemoryPollingConnector.Server(store, 3);

    @Override
    protected SourceConnector connector() {
        return new InMemoryPollingConnector(server);
    }

    @Override
    protected SourceConfig sourceConfig() {
        return new SourceConfig(1, 3, SourceTypes.CONNECTOR, "in-memory-poll", JsonMapper.builder().build().readTree("{}"),
                null, null);
    }

    @Override
    protected ContractPeer peer() {
        return server;
    }

    @Override
    protected PollCursorStore cursorStore() {
        return store;
    }
}
