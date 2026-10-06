package net.java21.data2flow.contracts.http;

import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 서비스 간 내부 호출용 JDK HttpClient(ADR-021·ADR-059: 내부 통신은 평문 HTTP 80).
 *
 * <p>JDK HttpClient의 기본 버전은 HTTP/2라서 평문 {@code http://}에 {@code Connection: Upgrade, HTTP2-Settings}·{@code Upgrade: h2c}
 * 헤더를 붙인다. uvicorn(httptools)은 지원하지 않는 업그레이드 요청의 본문을 버리므로 core→analytics POST가 빈 본문(400)이 됐다.
 * 내부 호출은 모두 이 클래스로 만들어 HTTP/1.1로 고정한다. 외부 HTTPS 호출(ALPN으로 협상)은 해당 없음.
 */
public final class InternalHttpClients {

    private InternalHttpClients() {
    }

    /** HTTP/1.1로 고정한 빌더. 리다이렉트 정책 등을 더 정할 때 쓴다 */
    public static HttpClient.Builder newBuilder(Duration connectTimeout) {
        return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(connectTimeout);
    }

    /** HTTP/1.1로 고정한 클라이언트 */
    public static HttpClient create(Duration connectTimeout) {
        return newBuilder(connectTimeout).build();
    }

    /** RestClient용 요청 팩토리(HTTP/1.1, 연결·읽기 시간 제한). spring-web이 있는 서비스에서만 쓴다 */
    public static JdkClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(create(connectTimeout));
        if (readTimeout != null) {
            factory.setReadTimeout(readTimeout);
        }
        return factory;
    }
}
