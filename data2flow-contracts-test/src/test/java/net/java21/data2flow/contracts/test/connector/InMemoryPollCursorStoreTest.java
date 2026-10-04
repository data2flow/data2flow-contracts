package net.java21.data2flow.contracts.test.connector;

import net.java21.data2flow.contracts.connector.LeaseLostException;
import net.java21.data2flow.contracts.connector.PollCursor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** DSC-09.10 BR-DSC-26: 리스를 잃은 뒤의 커서 저장은 거부된다 */
class InMemoryPollCursorStoreTest {

    @Test
    @DisplayName("DSC-09.10 TC-DSC-295 리스를 잃으면 커서 저장이 LeaseLostException으로 거부되고 이전 위치가 남는다")
    void revokedLeaseRejectsSave() {
        InMemoryPollCursorStore store = new InMemoryPollCursorStore();
        store.save(3, PollCursor.at("10"));
        assertThat(store.saves()).isEqualTo(1);
        store.revokeLease();
        assertThatThrownBy(() -> store.save(3, PollCursor.at("20"))).isInstanceOf(LeaseLostException.class);
        assertThat(store.load(3)).contains(PollCursor.at("10"));
        assertThat(store.load(4)).isEmpty();
    }
}
