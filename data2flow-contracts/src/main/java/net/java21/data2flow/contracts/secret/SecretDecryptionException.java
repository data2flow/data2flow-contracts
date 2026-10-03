package net.java21.data2flow.contracts.secret;

/** 비밀값을 풀 수 없다(키 없음, 변조, 다른 context). 문구에 비밀값·키를 넣지 않는다 */
public class SecretDecryptionException extends RuntimeException {

    public SecretDecryptionException(String message) {
        super(message);
    }
}
