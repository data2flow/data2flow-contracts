package net.java21.data2flow.contracts.message;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 플랫폼 브로커 기기 payload 서명 형식(DSC-03.02·03.03, BR-DSC-12, ADR-042). 기기(펌웨어)·simulator·ingress·시험이 같은 규칙을 쓴다.
 *
 * <pre>
 * payload = "v1." + hex(HMAC-SHA256(signingKey, body)) + "." + body
 * </pre>
 *
 * <ul>
 *   <li>{@code signingKey}: 승인 때 한 번 보여 준 서명 키 문자열의 UTF-8 바이트</li>
 *   <li>{@code body}: 기기가 보내려던 본문 그대로(보통 JSON). 서명은 이 바이트에 대해 계산한다</li>
 *   <li>hex는 소문자 64자. MQTT 3.1.1에도 헤더가 없어서 본문 앞에 붙인다. 펌웨어는 문자열을 이어 붙이기만 하면 된다</li>
 * </ul>
 */
public final class PlatformBrokerSignature {

    public static final String PREFIX = "v1.";
    private static final int HEX_LENGTH = 64;
    private static final int HEADER_LENGTH = PREFIX.length() + HEX_LENGTH + 1;
    private static final HexFormat HEX = HexFormat.of();

    private PlatformBrokerSignature() {
    }

    /** 서명 접두사를 뗀 결과 */
    public record Signed(String signatureHex, byte[] body) {
    }

    /** body에 서명을 붙인 payload를 만든다 */
    public static byte[] sign(String signingKey, byte[] body) {
        byte[] header = (PREFIX + HEX.formatHex(hmac(signingKey, body)) + ".").getBytes(StandardCharsets.US_ASCII);
        byte[] out = Arrays.copyOf(header, header.length + body.length);
        System.arraycopy(body, 0, out, header.length, body.length);
        return out;
    }

    /** payload가 서명 형식이면 서명과 본문으로 나눈다. 형식이 아니면(서명 없음) 빈 값 */
    public static Optional<Signed> parse(byte[] payload) {
        if (payload == null || payload.length < HEADER_LENGTH || payload[HEADER_LENGTH - 1] != '.') {
            return Optional.empty();
        }
        for (int i = 0; i < PREFIX.length(); i++) {
            if (payload[i] != PREFIX.charAt(i)) {
                return Optional.empty();
            }
        }
        for (int i = PREFIX.length(); i < HEADER_LENGTH - 1; i++) {
            byte b = payload[i];
            if (!(b >= '0' && b <= '9' || b >= 'a' && b <= 'f' || b >= 'A' && b <= 'F')) {
                return Optional.empty();
            }
        }
        String hex = new String(payload, PREFIX.length(), HEX_LENGTH, StandardCharsets.US_ASCII).toLowerCase();
        return Optional.of(new Signed(hex, Arrays.copyOfRange(payload, HEADER_LENGTH, payload.length)));
    }

    /** 서명이 이 키로 계산한 값과 같은가(상수 시간 비교) */
    public static boolean verify(String signingKey, Signed signed) {
        byte[] expected = hmac(signingKey, signed.body());
        byte[] actual;
        try {
            actual = HEX.parseHex(signed.signatureHex());
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(expected, actual);
    }

    static byte[] hmac(String signingKey, byte[] body) {
        if (signingKey == null || signingKey.isEmpty()) {
            throw new IllegalArgumentException("서명 키가 비어 있습니다");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(body);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256을 쓸 수 없습니다", e);
        }
    }
}
