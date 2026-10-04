package net.java21.data2flow.contracts.external;

/**
 * 기상청 동네예보 격자 변환(DSC-06.01, BR-DSC-15: 사이트 좌표가 바뀌면 격자를 다시 계산). 위경도(WGS84) → 격자 (nx, ny)와 그 반대.
 * 기상청이 공개한 람베르트 정각원추도법(LCC) 변환식과 상수(격자 5km, 표준 위도 30°·60°, 기준점 126°E·38°N → (43, 136))를 쓴다.
 * core-api(사이트 저장 때 격자 계산·표시)와 ingress(초단기실황·단기예보 폴링)가 같은 결과를 내도록 계약에 둔다.
 */
public final class KmaGrid {

    private static final double RE = 6371.00877;   // 지구 반경(km)
    private static final double GRID = 5.0;        // 격자 간격(km)
    private static final double SLAT1 = 30.0;
    private static final double SLAT2 = 60.0;
    private static final double OLON = 126.0;
    private static final double OLAT = 38.0;
    private static final double XO = 43;
    private static final double YO = 136;
    private static final double DEGRAD = Math.PI / 180.0;
    private static final double RADDEG = 180.0 / Math.PI;

    private static final double RE_G = RE / GRID;
    private static final double SN;
    private static final double SF;
    private static final double RO;

    static {
        double slat1 = SLAT1 * DEGRAD;
        double slat2 = SLAT2 * DEGRAD;
        double olat = OLAT * DEGRAD;
        double sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        SN = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);
        double sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        SF = Math.pow(sf, SN) * Math.cos(slat1) / SN;
        double ro = Math.tan(Math.PI * 0.25 + olat * 0.5);
        RO = RE_G * SF / Math.pow(ro, SN);
    }

    private KmaGrid() {
    }

    /** 위경도 → 격자. 한반도 밖 좌표도 계산은 하지만 격자 범위(1~149, 1~253) 밖이면 {@link IllegalArgumentException} */
    public static Point toGrid(double lat, double lng) {
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("위경도 범위를 벗어났습니다: " + lat + ", " + lng);
        }
        double ra = Math.tan(Math.PI * 0.25 + lat * DEGRAD * 0.5);
        ra = RE_G * SF / Math.pow(ra, SN);
        double theta = lng * DEGRAD - OLON * DEGRAD;
        if (theta > Math.PI) {
            theta -= 2.0 * Math.PI;
        }
        if (theta < -Math.PI) {
            theta += 2.0 * Math.PI;
        }
        theta *= SN;
        int nx = (int) Math.floor(ra * Math.sin(theta) + XO + 0.5);
        int ny = (int) Math.floor(RO - ra * Math.cos(theta) + YO + 0.5);
        return new Point(nx, ny);
    }

    /** 격자 → 격자 중심 위경도(표시용) */
    public static double[] toLatLng(Point p) {
        double xn = p.nx() - XO;
        double yn = RO - p.ny() + YO;
        double ra = Math.sqrt(xn * xn + yn * yn);
        if (SN < 0.0) {
            ra = -ra;
        }
        double alat = Math.pow(RE_G * SF / ra, 1.0 / SN);
        alat = 2.0 * Math.atan(alat) - Math.PI * 0.5;
        double theta;
        if (Math.abs(xn) <= 0.0) {
            theta = 0.0;
        } else if (Math.abs(yn) <= 0.0) {
            theta = Math.PI * 0.5;
            if (xn < 0.0) {
                theta = -theta;
            }
        } else {
            theta = Math.atan2(xn, yn);
        }
        double alon = theta / SN + OLON * DEGRAD;
        return new double[]{alat * RADDEG, alon * RADDEG};
    }

    /**
     * 격자 점. 동네예보 API {@code nx}, {@code ny} 값이다.
     *
     * @param nx 동서 격자(1~149)
     * @param ny 남북 격자(1~253)
     */
    public record Point(int nx, int ny) {

        public static final int MAX_NX = 149;
        public static final int MAX_NY = 253;

        public Point {
            if (nx < 1 || nx > MAX_NX || ny < 1 || ny > MAX_NY) {
                throw new IllegalArgumentException("기상청 격자 범위 밖입니다: (" + nx + ", " + ny + ")");
            }
        }
    }
}
