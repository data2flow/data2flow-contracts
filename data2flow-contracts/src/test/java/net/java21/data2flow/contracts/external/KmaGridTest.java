package net.java21.data2flow.contracts.external;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** DSC-06.01 BR-DSC-15 기상청 격자 변환(공개 변환식): 기상청 격자 좌표표의 대표 지점과 같아야 한다 */
class KmaGridTest {

    @ParameterizedTest
    @CsvSource({
            "37.5635694444444, 126.980008333333, 60, 127",   // 서울 중구
            "35.1770194444444, 129.076952777777, 98, 76",    // 부산 부산진구
            "33.4996213, 126.5311884, 53, 38",               // 제주시
            "36.3504119, 127.3845475, 67, 100"               // 대전
    })
    @DisplayName("DSC-06.01 TC-DSC-133 사이트 좌표 → 동네예보 격자(nx, ny)")
    void toGrid(double lat, double lng, int nx, int ny) {
        assertThat(KmaGrid.toGrid(lat, lng)).isEqualTo(new KmaGrid.Point(nx, ny));
    }

    @Test
    @DisplayName("DSC-06.01 격자 → 중심 위경도는 다시 같은 격자가 되고, 범위 밖 좌표·격자는 거부한다")
    void inverseAndRange() {
        KmaGrid.Point seoul = new KmaGrid.Point(60, 127);
        double[] latLng = KmaGrid.toLatLng(seoul);
        assertThat(latLng[0]).isCloseTo(37.57, within(0.05));
        assertThat(latLng[1]).isCloseTo(126.98, within(0.05));
        assertThat(KmaGrid.toGrid(latLng[0], latLng[1])).isEqualTo(seoul);
        assertThat(KmaGrid.toLatLng(new KmaGrid.Point(43, 136))[1]).isCloseTo(126.0, within(0.001));
        assertThat(KmaGrid.toLatLng(new KmaGrid.Point(50, 136))[0]).isGreaterThan(37.9);
        assertThatThrownBy(() -> KmaGrid.toGrid(91, 127)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KmaGrid.toGrid(37.5, 181)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KmaGrid.toGrid(51.5, -0.12)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KmaGrid.Point(0, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
