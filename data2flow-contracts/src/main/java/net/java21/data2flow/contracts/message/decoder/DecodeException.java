package net.java21.data2flow.contracts.message.decoder;

/**
 * 원본을 해석하지 못했다(처리 상태 {@code DECODE_ERROR}, 결과 코드 {@value #RESULT_CODE}, ING domain-model).
 * 재시도해도 결과가 같으므로 원본에 상태를 남기고 다음 메시지로 넘어간다. 디코더를 고친 뒤 재처리(ING-01.04)한다.
 */
public class DecodeException extends Exception {

    public static final String RESULT_CODE = "ING_DECODE_FAILED";

    private final String decoderKey;

    public DecodeException(String decoderKey, String message) {
        super(message);
        this.decoderKey = decoderKey;
    }

    public DecodeException(String decoderKey, String message, Throwable cause) {
        super(message, cause);
        this.decoderKey = decoderKey;
    }

    public String decoderKey() {
        return decoderKey;
    }
}
