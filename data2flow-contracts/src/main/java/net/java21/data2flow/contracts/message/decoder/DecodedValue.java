package net.java21.data2flow.contracts.message.decoder;

/**
 * 디코딩된 측정값 하나(원본 키, 별칭 변환 전).
 *
 * <p>값은 숫자·문자열·불린 중 하나다. 문자열·불린(예: {@code magnet_status: "open"})은 측정 항목 정의의 상태 매핑으로
 * 숫자가 된다(ING-02.06, open/close → 1/0). 숫자로 바꿀 수 없으면 {@code DECODE_ERROR}.
 *
 * @param key   원본 측정 키
 * @param value {@link Number}, {@link String}, {@link Boolean}
 * @param unit  payload가 알려 준 단위. 없으면 null
 */
public record DecodedValue(String key, Object value, String unit) {

    public DecodedValue {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("측정 키가 비어 있습니다");
        }
        if (!(value instanceof Number || value instanceof String || value instanceof Boolean)) {
            throw new IllegalArgumentException("측정값은 숫자·문자열·불린이어야 합니다: " + key);
        }
    }

    public static DecodedValue of(String key, double value) {
        return new DecodedValue(key, value, null);
    }

    public boolean isNumeric() {
        return value instanceof Number;
    }

    /** 숫자 값. 숫자가 아니면 {@link IllegalStateException} */
    public double asDouble() {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        throw new IllegalStateException(key + " 값은 숫자가 아닙니다: " + value);
    }
}
