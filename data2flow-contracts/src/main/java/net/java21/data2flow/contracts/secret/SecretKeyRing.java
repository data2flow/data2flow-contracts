package net.java21.data2flow.contracts.secret;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 비밀값 암호화 마스터 키 묶음(operations/security-operations.md #16, Secret {@code data2flow-master-key}).
 * 키마다 ID(kid)를 붙여 교체한다: 새 키 추가 → 활성 키를 새 키로 → 재암호화 배치 → 이전 키 제거.
 * 암호화는 활성 키로만 하고, 복호화는 암호문에 적힌 kid의 키로 한다.
 */
public final class SecretKeyRing {

    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9._-]{1,32}");
    private static final int KEY_BYTES = 32; // AES-256

    private final Map<String, SecretKey> keys;
    private final String activeKeyId;

    public SecretKeyRing(Map<String, byte[]> rawKeys, String activeKeyId) {
        if (rawKeys == null || rawKeys.isEmpty()) {
            throw new IllegalArgumentException("마스터 키가 없습니다");
        }
        Map<String, SecretKey> parsed = new LinkedHashMap<>();
        rawKeys.forEach((kid, raw) -> {
            if (!KEY_ID.matcher(kid).matches()) {
                throw new IllegalArgumentException("키 ID 형식이 아닙니다: " + kid);
            }
            if (raw == null || raw.length != KEY_BYTES) {
                throw new IllegalArgumentException("키 " + kid + "는 32바이트(AES-256)여야 합니다");
            }
            parsed.put(kid, new SecretKeySpec(raw, "AES"));
        });
        String active = activeKeyId == null || activeKeyId.isBlank()
                ? (parsed.size() == 1 ? parsed.keySet().iterator().next() : null) : activeKeyId;
        if (active == null || !parsed.containsKey(active)) {
            throw new IllegalArgumentException("활성 키 ID가 키 목록에 없습니다: " + activeKeyId);
        }
        this.keys = Collections.unmodifiableMap(parsed);
        this.activeKeyId = active;
    }

    /**
     * 설정 문자열에서 읽는다. 형식: {@code kid1:BASE64,kid2:BASE64}(키는 Base64로 인코딩한 32바이트).
     * 환경변수 예: {@code DATA2FLOW_SECRETS_MASTER_KEYS=k2026a:…}, {@code DATA2FLOW_SECRETS_ACTIVE_KEY_ID=k2026a}
     */
    public static SecretKeyRing parse(String spec, String activeKeyId) {
        if (spec == null || spec.isBlank()) {
            throw new IllegalArgumentException("마스터 키가 없습니다");
        }
        Map<String, byte[]> raw = new LinkedHashMap<>();
        for (String entry : spec.split(",")) {
            String trimmed = entry.strip();
            int colon = trimmed.indexOf(':');
            if (colon <= 0) {
                throw new IllegalArgumentException("키 항목은 kid:BASE64 형식이어야 합니다");
            }
            try {
                raw.put(trimmed.substring(0, colon), Base64.getDecoder().decode(trimmed.substring(colon + 1).strip()));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("키 " + trimmed.substring(0, colon) + "가 Base64가 아닙니다");
            }
        }
        return new SecretKeyRing(raw, activeKeyId);
    }

    public String activeKeyId() {
        return activeKeyId;
    }

    SecretKey activeKey() {
        return keys.get(activeKeyId);
    }

    Optional<SecretKey> key(String keyId) {
        return Optional.ofNullable(keys.get(keyId));
    }

    public Set<String> keyIds() {
        return keys.keySet();
    }

    @Override
    public String toString() {
        return "SecretKeyRing[active=" + activeKeyId + ", keyIds=" + keys.keySet() + "]";
    }
}
