package net.java21.data2flow.contracts.secret;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 비밀값 저장 암호화(NFR-03.02): AES-256-GCM, 키 ID 접두로 키 교체를 지원한다.
 *
 * <p>암호문(bytea) 구조: {@code [버전 0x01][kid 길이 1바이트][kid][IV 12바이트][암호문 + 태그 16바이트]}.
 * 버전·kid는 AAD(추가 인증 데이터)에 들어가 바꿔치기할 수 없다. {@code context}(예: {@code "data2flow_core.users.totp_secret:42"})를
 * 함께 넘기면 다른 행·열로 옮긴 암호문은 풀리지 않는다. 같은 값을 넣어도 IV가 매번 달라 암호문이 다르다.
 * 문자열 열(varchar·jsonb)에는 {@link #encryptToText}의 {@code enc:v1:<Base64>} 형식을 쓴다.
 */
public class SecretCipher {

    static final byte VERSION = 1;
    static final String TEXT_PREFIX = "enc:v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeyRing keyRing;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(SecretKeyRing keyRing) {
        this.keyRing = keyRing;
    }

    public byte[] encrypt(Secret secret, String context) {
        return encrypt(secret.reveal().getBytes(StandardCharsets.UTF_8), context);
    }

    public byte[] encrypt(byte[] plaintext, String context) {
        String kid = keyRing.activeKeyId();
        byte[] header = header(kid);
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keyRing.activeKey(), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(header, context));
            byte[] sealed = cipher.doFinal(plaintext);
            ByteArrayOutputStream out = new ByteArrayOutputStream(header.length + IV_BYTES + sealed.length);
            out.writeBytes(header);
            out.writeBytes(iv);
            out.writeBytes(sealed);
            return out.toByteArray();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("비밀값 암호화 실패", ex);
        }
    }

    /** 복호화. 키가 없거나 암호문·context가 맞지 않으면 {@link SecretDecryptionException} */
    public Secret decrypt(byte[] ciphertext, String context) {
        return Secret.of(new String(decryptBytes(ciphertext, context), StandardCharsets.UTF_8));
    }

    public byte[] decryptBytes(byte[] ciphertext, String context) {
        String kid = keyId(ciphertext);
        SecretKey key = keyRing.key(kid).orElseThrow(() -> new SecretDecryptionException("알 수 없는 키 ID: " + kid));
        int headerLength = 2 + ciphertext[1];
        if (ciphertext.length < headerLength + IV_BYTES + TAG_BITS / 8) {
            throw new SecretDecryptionException("암호문이 너무 짧습니다");
        }
        byte[] header = Arrays.copyOfRange(ciphertext, 0, headerLength);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_BITS, ciphertext, headerLength, IV_BYTES));
            cipher.updateAAD(aad(header, context));
            return cipher.doFinal(ciphertext, headerLength + IV_BYTES, ciphertext.length - headerLength - IV_BYTES);
        } catch (GeneralSecurityException ex) {
            throw new SecretDecryptionException("비밀값 복호화 실패(키·context 불일치 또는 변조)");
        }
    }

    /** 암호문을 만든 키 ID */
    public String keyId(byte[] ciphertext) {
        if (ciphertext == null || ciphertext.length < 3 || ciphertext[0] != VERSION
                || ciphertext[1] <= 0 || ciphertext.length < 2 + ciphertext[1]) {
            throw new SecretDecryptionException("암호문 형식이 아닙니다");
        }
        return new String(ciphertext, 2, ciphertext[1], StandardCharsets.US_ASCII);
    }

    /** 활성 키가 아닌 키로 암호화돼 재암호화 배치 대상인가 */
    public boolean needsReencryption(byte[] ciphertext) {
        return !keyRing.activeKeyId().equals(keyId(ciphertext));
    }

    /** 활성 키로 다시 암호화한다(키 교체 배치) */
    public byte[] reencrypt(byte[] ciphertext, String context) {
        return encrypt(decryptBytes(ciphertext, context), context);
    }

    public String encryptToText(Secret secret, String context) {
        return TEXT_PREFIX + Base64.getEncoder().encodeToString(encrypt(secret, context));
    }

    public Secret decryptText(String text, String context) {
        if (text == null || !text.startsWith(TEXT_PREFIX)) {
            throw new SecretDecryptionException("암호문 문자열 형식이 아닙니다");
        }
        try {
            return decrypt(Base64.getDecoder().decode(text.substring(TEXT_PREFIX.length())), context);
        } catch (IllegalArgumentException ex) {
            throw new SecretDecryptionException("암호문 문자열이 Base64가 아닙니다");
        }
    }

    private static byte[] header(String kid) {
        byte[] kidBytes = kid.getBytes(StandardCharsets.US_ASCII);
        byte[] header = new byte[2 + kidBytes.length];
        header[0] = VERSION;
        header[1] = (byte) kidBytes.length;
        System.arraycopy(kidBytes, 0, header, 2, kidBytes.length);
        return header;
    }

    private static byte[] aad(byte[] header, String context) {
        byte[] ctx = (context == null ? "" : context).getBytes(StandardCharsets.UTF_8);
        byte[] aad = Arrays.copyOf(header, header.length + ctx.length);
        System.arraycopy(ctx, 0, aad, header.length, ctx.length);
        return aad;
    }

    @Override
    public String toString() {
        return "SecretCipher[" + keyRing + "]";
    }
}
