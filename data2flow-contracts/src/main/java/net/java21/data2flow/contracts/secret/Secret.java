package net.java21.data2flow.contracts.secret;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 비밀값(브로커 비밀번호, API 키, LLM 키, 기기 자격증명)을 담는 값 타입(NFR-03.02).
 *
 * <p>평문은 {@link #reveal()}으로만 꺼낸다. {@code toString()}, JSON 직렬화, 로그 출력은 언제나 {@value #MASK}이므로
 * DTO나 로그에 실수로 넣어도 평문이 나가지 않는다. 요청 본문의 문자열은 그대로 받아들인다(쓰기 전용 필드).
 */
public final class Secret {

    public static final String MASK = "***";

    private final String value;

    private Secret(String value) {
        this.value = value;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Secret of(String value) {
        if (value == null) {
            throw new IllegalArgumentException("비밀값이 null입니다");
        }
        return new Secret(value);
    }

    /** 평문. 저장 직전 암호화, 외부 시스템 접속에만 쓴다 */
    public String reveal() {
        return value;
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    /** JSON에는 항상 가린 값이 나간다 */
    @JsonValue
    public String masked() {
        return MASK;
    }

    @Override
    public String toString() {
        return MASK;
    }

    /** 시간 차이로 값이 드러나지 않게 비교한다 */
    @Override
    public boolean equals(Object o) {
        return o instanceof Secret other && MessageDigest.isEqual(
                value.getBytes(StandardCharsets.UTF_8), other.value.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public int hashCode() {
        return Secret.class.hashCode();
    }
}
