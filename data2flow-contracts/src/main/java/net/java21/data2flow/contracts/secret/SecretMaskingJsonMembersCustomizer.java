package net.java21.data2flow.contracts.secret;

import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * 구조화(JSON) 로그의 모든 문자열 값에서 비밀값을 가린다(NFR-03.02, AT-NFR-08.2). staging·prod는 ECS JSON 로그를 쓰므로
 * 서비스 설정에 아래 한 줄을 넣는다.
 *
 * <pre>logging.structured.json.customizer: net.java21.data2flow.contracts.secret.SecretMaskingJsonMembersCustomizer</pre>
 */
public class SecretMaskingJsonMembersCustomizer implements StructuredLoggingJsonMembersCustomizer<Object> {

    @Override
    public void customize(JsonWriter.Members<Object> members) {
        members.applyingValueProcessor(JsonWriter.ValueProcessor.of(String.class, SecretMasker::mask));
    }
}
