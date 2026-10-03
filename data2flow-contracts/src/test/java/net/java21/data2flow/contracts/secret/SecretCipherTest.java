package net.java21.data2flow.contracts.secret;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** NFR-03.02: 비밀값 암호화 저장(AES-256-GCM, 키 ID로 교체) */
class SecretCipherTest {

    private static final String K1 = key((byte) 1);
    private static final String K2 = key((byte) 2);
    private static final String CONTEXT = "data2flow_core.data_sources.secret:12";

    @Test
    @DisplayName("[NFR-03.02][TC-NFR-028] 암호문에는 평문이 없고, 같은 값도 매번 다른 암호문, 같은 context로만 풀린다")
    void roundTrip() {
        SecretCipher cipher = new SecretCipher(SecretKeyRing.parse("k1:" + K1, null));
        byte[] a = cipher.encrypt(Secret.of("broker-pass-1"), CONTEXT);
        byte[] b = cipher.encrypt(Secret.of("broker-pass-1"), CONTEXT);
        assertThat(a).isNotEqualTo(b);
        assertThat(new String(a, StandardCharsets.ISO_8859_1)).doesNotContain("broker-pass-1");
        assertThat(cipher.keyId(a)).isEqualTo("k1");
        assertThat(cipher.decrypt(a, CONTEXT).reveal()).isEqualTo("broker-pass-1");
        assertThatThrownBy(() -> cipher.decrypt(a, "data2flow_core.data_sources.secret:13"))
                .isInstanceOf(SecretDecryptionException.class);
    }

    @Test
    @DisplayName("[NFR-03.02] 변조된 암호문, 모르는 키, 형식이 아닌 값은 풀지 않는다")
    void tampering() {
        SecretCipher cipher = new SecretCipher(SecretKeyRing.parse("k1:" + K1, "k1"));
        byte[] sealed = cipher.encrypt("x".getBytes(StandardCharsets.UTF_8), null);
        sealed[sealed.length - 1] ^= 1;
        assertThatThrownBy(() -> cipher.decryptBytes(sealed, null)).isInstanceOf(SecretDecryptionException.class);

        SecretCipher other = new SecretCipher(SecretKeyRing.parse("k9:" + K2, null));
        byte[] foreign = other.encrypt(Secret.of("x"), null);
        assertThatThrownBy(() -> cipher.decrypt(foreign, null)).hasMessageContaining("k9");
        assertThatThrownBy(() -> cipher.keyId(new byte[]{9, 1, 1})).isInstanceOf(SecretDecryptionException.class);
        assertThatThrownBy(() -> cipher.keyId(null)).isInstanceOf(SecretDecryptionException.class);
        assertThatThrownBy(() -> cipher.decryptBytes(new byte[]{1, 2, 'k', '1', 0}, null)).hasMessageContaining("짧");
    }

    @Test
    @DisplayName("[NFR-03.02] 키 교체: 새 키를 활성화해도 이전 키 암호문은 풀리고, 재암호화 대상을 알려 주고 새 키로 다시 암호화한다")
    void rotation() {
        SecretCipher before = new SecretCipher(SecretKeyRing.parse("k1:" + K1, "k1"));
        byte[] old = before.encrypt(Secret.of("device-cred"), CONTEXT);

        SecretCipher after = new SecretCipher(SecretKeyRing.parse("k1:" + K1 + ", k2:" + K2, "k2"));
        assertThat(after.needsReencryption(old)).isTrue();
        byte[] renewed = after.reencrypt(old, CONTEXT);
        assertThat(after.keyId(renewed)).isEqualTo("k2");
        assertThat(after.needsReencryption(renewed)).isFalse();
        assertThat(after.decrypt(renewed, CONTEXT).reveal()).isEqualTo("device-cred");
        assertThat(after.decrypt(old, CONTEXT).reveal()).isEqualTo("device-cred");
    }

    @Test
    @DisplayName("[NFR-03.02] 문자열 열용 enc:v1: 형식")
    void textForm() {
        SecretCipher cipher = new SecretCipher(SecretKeyRing.parse("k1:" + K1, null));
        String text = cipher.encryptToText(Secret.of("llm-key"), "ai.llm");
        assertThat(text).startsWith("enc:v1:").doesNotContain("llm-key");
        assertThat(cipher.decryptText(text, "ai.llm").reveal()).isEqualTo("llm-key");
        assertThatThrownBy(() -> cipher.decryptText("plain", "ai.llm")).isInstanceOf(SecretDecryptionException.class);
        assertThatThrownBy(() -> cipher.decryptText("enc:v1:%%%", "ai.llm")).isInstanceOf(SecretDecryptionException.class);
        assertThat(cipher.toString()).doesNotContain(K1);
    }

    @Test
    @DisplayName("[NFR-03.02] 키 설정이 틀리면 기동 단계에서 실패한다(키 없음, 길이, Base64, 활성 키 모름, kid 형식)")
    void keyRingValidation() {
        assertThatThrownBy(() -> SecretKeyRing.parse(" ", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SecretKeyRing.parse("k1:" + Base64.getEncoder().encodeToString(new byte[16]), null))
                .hasMessageContaining("32");
        assertThatThrownBy(() -> SecretKeyRing.parse("k1:not-base64!", null)).hasMessageContaining("Base64");
        assertThatThrownBy(() -> SecretKeyRing.parse("nokid", null)).hasMessageContaining("kid:BASE64");
        assertThatThrownBy(() -> SecretKeyRing.parse("k1:" + K1 + ",k2:" + K2, null)).hasMessageContaining("활성");
        assertThatThrownBy(() -> SecretKeyRing.parse("k1:" + K1, "k3")).hasMessageContaining("활성");
        assertThatThrownBy(() -> SecretKeyRing.parse("bad kid:" + K1, null)).hasMessageContaining("ID");
        assertThatThrownBy(() -> new SecretKeyRing(Map.of(), null)).isInstanceOf(IllegalArgumentException.class);
        SecretKeyRing ring = SecretKeyRing.parse("k1:" + K1 + ",k2:" + K2, "k1");
        assertThat(ring.keyIds()).containsExactly("k1", "k2");
        assertThat(ring.toString()).doesNotContain(K1);
    }

    private static String key(byte fill) {
        byte[] raw = new byte[32];
        java.util.Arrays.fill(raw, fill);
        return Base64.getEncoder().encodeToString(raw);
    }
}
