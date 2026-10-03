package net.java21.data2flow.contracts.message;

import java.util.List;
import java.util.Map;

/** 메시지 record 생성자 검증 도우미. 위반은 {@link MessageFormatException}(DLQ 대상)이다 */
final class Messages {

    private Messages() {
    }

    static void requireVersion(int v) {
        if (v < 1) {
            throw new MessageFormatException("v는 1 이상이어야 합니다: " + v);
        }
    }

    /** ID는 IDENTITY라 1 이상이다. JSON에 필드가 없으면 0으로 읽히므로 이것으로 누락을 잡는다 */
    static long requireId(long value, String field) {
        if (value < 1) {
            throw new MessageFormatException(field + "은(는) 1 이상인 ID여야 합니다: " + value);
        }
        return value;
    }

    static <T> T require(T value, String field) {
        if (value == null) {
            throw new MessageFormatException(field + "은(는) 필수입니다");
        }
        return value;
    }

    static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new MessageFormatException(field + "은(는) 비어 있을 수 없습니다");
        }
        return value;
    }

    static <T> List<T> list(List<T> value) {
        return value == null ? List.of() : List.copyOf(value);
    }

    static <K, V> Map<K, V> map(Map<K, V> value) {
        return value == null ? Map.of() : Map.copyOf(value);
    }
}
