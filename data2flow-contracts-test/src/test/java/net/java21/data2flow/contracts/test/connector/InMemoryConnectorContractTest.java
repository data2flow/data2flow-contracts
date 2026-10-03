package net.java21.data2flow.contracts.test.connector;

import net.java21.data2flow.contracts.connector.SourceConfig;
import net.java21.data2flow.contracts.connector.SourceConnector;
import net.java21.data2flow.contracts.message.SourceTypes;
import tools.jackson.databind.json.JsonMapper;

/** DSC-09.02: 올바른 커넥터는 키트의 모든 시나리오를 통과한다(서비스 커넥터 테스트가 상속하는 방법의 예) */
class InMemoryConnectorContractTest extends AbstractConnectorContractTest {

    private final InMemoryConnector.Broker broker = new InMemoryConnector.Broker();

    @Override
    protected SourceConnector connector() {
        return new InMemoryConnector(broker, InMemoryConnector.Behavior.CORRECT);
    }

    @Override
    protected SourceConfig sourceConfig() {
        return new SourceConfig(1, 3, SourceTypes.CONNECTOR, "in-memory", JsonMapper.builder().build().readTree("{}"),
                null, null);
    }

    @Override
    protected ContractPeer peer() {
        return broker;
    }
}
