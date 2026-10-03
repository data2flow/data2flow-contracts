package net.java21.data2flow.contracts.identity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserHolderTest {

    @AfterEach
    void clear() {
        CurrentUserHolder.clear();
    }

    @Test
    @DisplayName("신원이 없으면 get은 실패하고 find는 비어 있다")
    void empty() {
        assertThatThrownBy(CurrentUserHolder::get).isInstanceOf(IllegalStateException.class);
        assertThat(CurrentUserHolder.find()).isEmpty();
    }

    @Test
    @DisplayName("callAs는 작업 동안만 신원을 바꾸고 이전 신원으로 되돌린다")
    void callAs() {
        CurrentUser outer = new CurrentUser(1, 1);
        CurrentUser inner = new CurrentUser(2, 1);
        assertThat(CurrentUserHolder.callAs(inner, () -> CurrentUserHolder.get().userId())).isEqualTo(2);
        assertThat(CurrentUserHolder.find()).isEmpty();

        CurrentUserHolder.set(outer);
        CurrentUserHolder.callAs(inner, () -> null);
        assertThat(CurrentUserHolder.get()).isSameAs(outer);
    }
}
