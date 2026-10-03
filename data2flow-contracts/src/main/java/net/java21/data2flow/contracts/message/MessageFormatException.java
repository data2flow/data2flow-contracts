package net.java21.data2flow.contracts.message;

/**
 * 메시지 본문을 계약대로 읽거나 쓸 수 없다(JSON 형식 오류, 필수 필드 누락, 값 범위 위반).
 *
 * <p>다시 시도해도 결과가 같으므로 소비자는 재시도하지 않고 DLQ로 보낸다(reliability-and-ha.md §2 "처리할 수 없는 메시지").
 */
public class MessageFormatException extends RuntimeException {

    public MessageFormatException(String message) {
        super(message);
    }

    public MessageFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
