package net.java21.data2flow.contracts.secret;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 비밀값 암호화 설정({@code data2flow.secrets.*}). 값은 k8s Secret {@code data2flow-master-key}(로컬은 .env)에서 환경변수로 넣는다.
 *
 * @param masterKeys  {@code kid:BASE64,kid2:BASE64}
 * @param activeKeyId 암호화에 쓸 키 ID(키가 하나면 생략 가능)
 */
@ConfigurationProperties(prefix = "data2flow.secrets")
public record SecretsProperties(String masterKeys, String activeKeyId) {

    @Override
    public String toString() {
        return "SecretsProperties[masterKeys=" + Secret.MASK + ", activeKeyId=" + activeKeyId + "]";
    }
}
