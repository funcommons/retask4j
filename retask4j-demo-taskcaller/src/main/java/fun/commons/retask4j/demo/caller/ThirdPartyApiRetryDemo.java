package fun.commons.retask4j.demo.caller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * Demo: third-party API call with automatic retry on failure.
 *
 * <p>This controller demonstrates the retask4j HTTP proxy's retry capability. It
 * forwards a POST request to {@code https://httpbin.org/status/500,200,200} via
 * the proxy's {@code /proxy/push} endpoint (NORMAL mode, fire-and-forget).
 * The remote endpoint returns HTTP 500 on the first call, then HTTP 200 on
 * subsequent calls. The retask4j worker will retry on assertion failure
 * according to the configured retry plan.
 *
 * <p>Headers added:
 * <ul>
 *   <li>{@code retask4j-retry-plan: [2,5,10]} - retry after 2s, 5s, then 10s</li>
 *   <li>{@code retask4j-assert-response: {"statusIn":[200]}} - assert response status 200</li>
 * </ul>
 */
@RestController
@RequestMapping("/demo/retry-payment")
@Slf4j
public class ThirdPartyApiRetryDemo {

    private static final String PROXY_BASE_URL = "http://localhost:9400";
    private static final String PROXY_PATH = "/proxy/push";
    private static final String TARGET_URL = "https://httpbin.org/status/500,200,200";
    private static final List<Integer> RETRY_PLAN = Arrays.asList(2, 5, 10);

    private final RestTemplate restTemplate = new RestTemplate();

    @RequestMapping
    public JSONObject retryPayment() {
        log.info("ThirdPartyApiRetryDemo invoked - submitting payment retry task");

        // Build the proxy URL: PROXY_PATH + "/" + TARGET_URL
        // The retask4j-http controller extracts the URL after the path prefix.
        String proxyUrl = PROXY_BASE_URL + PROXY_PATH + "/" + TARGET_URL;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Override the default retry plan with a custom one
        headers.set("retask4j-retry-plan", JSON.toJSONString(RETRY_PLAN));

        // Assert that the proxied response status must be 200
        JSONObject assertResponse = new JSONObject();
        assertResponse.put("statusIn", Arrays.asList(200));
        headers.set("retask4j-assert-response", assertResponse.toJSONString());

        // Body for the proxied request - httpbin.org/status/* ignores the body
        JSONObject body = new JSONObject();
        body.put("amount", 1000);
        body.put("currency", "USD");

        HttpEntity<String> entity = new HttpEntity<>(body.toJSONString(), headers);

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(URI.create(proxyUrl), HttpMethod.POST, entity, String.class);
        } catch (Exception e) {
            log.error("Failed to submit retry-payment task via proxy", e);
            JSONObject error = new JSONObject();
            error.put("status", "error");
            error.put("message", e.getMessage());
            return error;
        }

        JSONObject result = new JSONObject();
        result.put("status", response.getStatusCode().value());
        result.put("proxy_url", proxyUrl);
        result.put("target_url", TARGET_URL);
        result.put("expected_status", 200);
        result.put("retry_plan_seconds", RETRY_PLAN);
        result.put("assertion", assertResponse);
        result.put("description",
                "POSTs to httpbin.org/status/500,200,200. First call returns 500, "
                        + "worker retries per retask4j-retry-plan until assertion status=200 passes.");
        result.put("proxy_response", response.getBody());
        return result;
    }
}
