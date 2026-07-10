package fun.commons.retask4j.http.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Slf4j
public final class HttpClientFactory {

    private HttpClientFactory() {}

    public static HttpClientHolder create(int connectTimeoutMs, int readTimeoutMs) {
        PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
        connManager.setMaxTotal(200);
        connManager.setDefaultMaxPerRoute(50);
        connManager.setValidateAfterInactivity(TimeValue.ofSeconds(30));
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connManager)
                .evictIdleConnections(TimeValue.ofSeconds(60))
                // Disable automatic redirect handling to prevent SSRF bypass:
                // without this, HttpClient would follow 3xx redirects to private addresses
                // (e.g., 127.0.0.1, 169.254.169.254) without SSRF re-validation
                .disableRedirectHandling()
                .build();
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setConnectionRequestTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return new HttpClientHolder(httpClient, connManager, new RestTemplate(factory));
    }

    @Slf4j
    public static final class HttpClientHolder implements AutoCloseable {
        private final CloseableHttpClient httpClient;
        private final PoolingHttpClientConnectionManager connManager;
        private final RestTemplate restTemplate;

        private HttpClientHolder(CloseableHttpClient httpClient, PoolingHttpClientConnectionManager connManager, RestTemplate restTemplate) {
            this.httpClient = httpClient;
            this.connManager = connManager;
            this.restTemplate = restTemplate;
        }

        public CloseableHttpClient getHttpClient() { return httpClient; }
        public RestTemplate getRestTemplate() { return restTemplate; }

        @Override
        public void close() {
            try {
                // Closing the HttpClient also shuts down its ConnectionManager
                httpClient.close();
            } catch (Exception e) {
                log.warn("Error closing HttpClient", e);
            }
        }
    }
}
