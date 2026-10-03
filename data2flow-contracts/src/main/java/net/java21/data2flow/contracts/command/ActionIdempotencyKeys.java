package net.java21.data2flow.contracts.command;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 행동 요청 멱등 키(BR-ACT-02, BR-FLW-13, reliability-and-ha.md §2 ⑧). 같은 키는 {@code executed_actions}에 한 번만 기록되고 한 번만
 * 실행된다. 키는 소문자 16진수 64자(SHA-256)라 {@code commands.idempotency_key char(64)}에 그대로 들어간다.
 *
 * <p>플로우 키에는 <b>플로우 버전을 넣지 않는다</b>: 처리 뒤 오프셋 저장 전에 장애가 나 같은 메시지가 새 버전으로 다시 처리돼도 같은
 * 노드(안정 ID)의 행동은 한 번만 나간다. 입력은 필드를 U+001F(단위 구분 문자)로 이어 UTF-8로 해시한다.
 */
public final class ActionIdempotencyKeys {

    /** 키 최대 길이(사용자 Idempotency-Key 64자, SHA-256 16진수 64자) */
    public static final int MAX_LENGTH = 64;
    private static final char SEP = '\u001F';

    private ActionIdempotencyKeys() {
    }

    /** 플로우 행동: {@code sha256(flowId, nodeId, triggerMessageId)} */
    public static String flow(String flowId, String nodeId, String triggerMessageId) {
        return sha256(require(flowId, "flowId") + SEP + require(nodeId, "nodeId") + SEP
                + require(triggerMessageId, "triggerMessageId"));
    }

    /** 한 노드가 메시지 하나로 행동을 여러 개 낼 때(관계 대상 펼침 전 분할 등): {@code sha256(flowId, nodeId, triggerMessageId, index)} */
    public static String flow(String flowId, String nodeId, String triggerMessageId, int splitIndex) {
        if (splitIndex < 0) {
            throw new IllegalArgumentException("splitIndex는 0 이상이어야 합니다");
        }
        return sha256(require(flowId, "flowId") + SEP + require(nodeId, "nodeId") + SEP
                + require(triggerMessageId, "triggerMessageId") + SEP + splitIndex);
    }

    /** 그 밖의 생산자(예약 실행 {@code schedule}, 장면 {@code scene} 등): {@code sha256(scope, part…)} */
    public static String of(String scope, String... parts) {
        StringBuilder sb = new StringBuilder(require(scope, "scope"));
        for (String p : parts) {
            sb.append(SEP).append(require(p, "part"));
        }
        return sha256(sb.toString());
    }

    /** 키 형식 검사: 1~64자, 허용 문자 {@code A-Z a-z 0-9 . _ : -}(Idempotency-Key 규칙과 같음) */
    public static boolean isValid(String key) {
        return key != null && !key.isEmpty() && key.length() <= MAX_LENGTH && key.matches("[A-Za-z0-9._:-]+");
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "은(는) 비어 있을 수 없습니다");
        }
        return value;
    }

    private static String sha256(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
