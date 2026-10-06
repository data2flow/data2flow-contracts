package net.java21.data2flow.contracts.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ANA-04.01 내부 호출 HTTP/1.1 고정. JDK HttpClient 기본값(HTTP/2)은 평문 http에서 {@code Upgrade: h2c}를 붙이고,
 * uvicorn(httptools)은 이 요청의 본문을 버린다(core→analytics POST가 모두 400). 내부 호출 클라이언트는 이 헤더를 보내지 않아야 한다.
 */
class InternalHttpClientsTest {

    private HttpServer server;
    private final CopyOnWriteArrayList<String> upgrades = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Integer> bodyLengths = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            upgrades.add(String.valueOf(exchange.getRequestHeaders().getFirst("Upgrade")));
            bodyLengths.add(exchange.getRequestBody().readAllBytes().length);
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private URI uri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/internal/analytics/runs");
    }

    private static HttpRequest post(URI uri) {
        return HttpRequest.newBuilder(uri).POST(HttpRequest.BodyPublishers.ofString("{\"a\":1}"))
                .header("Content-Type", "application/json").build();
    }

    @Test
    @DisplayName("[ANA-04.01] 재현: JDK HttpClient 기본값은 평문 http POST에 Upgrade: h2c를 붙인다")
    void defaultClientSendsH2cUpgrade() throws Exception {
        try (HttpClient http = HttpClient.newHttpClient()) {
            http.send(post(uri()), HttpResponse.BodyHandlers.discarding());
        }
        assertThat(upgrades).containsExactly("h2c");
    }

    @Test
    @DisplayName("[ANA-04.01] InternalHttpClients는 HTTP/1.1로 고정해 Upgrade 헤더 없이 본문을 보낸다")
    void internalClientIsHttp11() throws Exception {
        try (HttpClient http = InternalHttpClients.create(Duration.ofSeconds(2))) {
            assertThat(http.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
            http.send(post(uri()), HttpResponse.BodyHandlers.discarding());
        }
        RestClient rest = RestClient.builder()
                .requestFactory(InternalHttpClients.requestFactory(Duration.ofSeconds(2), Duration.ofSeconds(5))).build();
        rest.post().uri(uri()).contentType(MediaType.APPLICATION_JSON).body("{\"b\":2}").retrieve().toBodilessEntity();
        assertThat(upgrades).containsExactly("null", "null");
        assertThat(bodyLengths).containsExactly(7, 7);
    }

    @Test
    @DisplayName("[ANA-04.01] Boot가 주입하는 RestClient.Builder(JDK 팩토리)도 HTTP/1.1로 보낸다. 끄면 기본값")
    void autoConfiguredBuilderIsHttp11() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(InternalHttpClientAutoConfiguration.class, HttpClientAutoConfiguration.class,
                        ImperativeHttpClientAutoConfiguration.class, RestClientAutoConfiguration.class))
                .withPropertyValues("spring.http.clients.imperative.factory=jdk");
        runner.run(context -> {
            RestClient rest = context.getBean(RestClient.Builder.class).build();
            rest.post().uri(uri()).contentType(MediaType.APPLICATION_JSON).body("{\"c\":3}").retrieve().toBodilessEntity();
        });
        assertThat(upgrades).containsExactly("null");
        assertThat(bodyLengths).containsExactly(7);

        runner.withPropertyValues("data2flow.http.force-http11=false").run(context -> {
            RestClient rest = context.getBean(RestClient.Builder.class).build();
            rest.post().uri(uri()).contentType(MediaType.APPLICATION_JSON).body("{\"c\":3}").retrieve().toBodilessEntity();
        });
        assertThat(upgrades).containsExactly("null", "h2c");
    }
}
