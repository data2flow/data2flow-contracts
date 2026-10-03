package net.java21.data2flow.contracts.message;

/** 측정값 품질 코드(ING domain-model {@code telemetry.quality}, TSD-03.04 필터) */
public final class Quality {

    /** 정상 */
    public static final int NORMAL = 0;
    /** 유효 범위 밖(ING-04.01) */
    public static final int OUT_OF_RANGE = 1;
    /** 미검증 측정 항목(ING-04.02) */
    public static final int UNVERIFIED = 2;
    /** 의심: 값 멈춤·급변 */
    public static final int SUSPECT = 3;
    /** 측정 시각 보정됨(ING-02.05) */
    public static final int TIME_CORRECTED = 4;
    /** 예보값(외부 맥락 예보, DSC-06) */
    public static final int FORECAST = 5;

    private Quality() {
    }
}
