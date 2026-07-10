package fun.commons.retask4j.demo.caller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Demo: async task with callback notification.
 *
 * <p>This controller submits a task via {@code /proxy/task} (CALLBACK mode).
 * The retask4j worker executes the proxied HTTP call to {@code httpbin.org/delay/2}
 * (which takes ~2 seconds), then POSTs the result to the configured
 * {@code retask4j-callback-url} once complete.
 *
 * <p>The callback receiver endpoint {@code /demo/callback-receiver} lives in the
 * same controller and just logs whatever it receives, so the demo is self-contained.
 */
@RestController
@Slf4j
public class CallbackDemo {

    private static final String PROXY_BASE_URL = "http://localhost:9400";
    private static final String PROXY_PATH = "/proxy/task";
    private static final String TARGET_URL = "https://httpbin.org/delay/2";
    private static final String CALLBACK_URL = "http://localhost:9090/demo/callback-receiver";

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Submit an async callback task. Returns immediately with task metadata;
     * the actual remote call runs in the background and triggers the callback.
     */
    @RequestMapping("/demo/async-callback")
    public JSONObject submitAsyncCallback(@RequestBody(required = false) JSONObject body) {
        if (body == null) {
            body = new JSONObject();
        }

        log.info("CallbackDemo invoked - submitting async callback task");

        String proxyUrl = PROXY_BASE_URL + PROXY_PATH + "/" + TARGET_URL;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Override the default callback URL. In CALLBACK mode the retask4j worker
        // POSTs the final HTTP response (status, headers, body) to this URL.
        headers.set("retask4j-callback-url", CALLBACK_URL);

        // Body sent to httpbin.org/delay/2 (it echoes back the JSON)
        JSONObject proxiedBody = new JSONObject();
        proxiedBody.put("purpose", "demonstrate async callback");
        proxiedBody.put("echo", body);

        HttpEntity<String> entity = new HttpEntity<>(proxiedBody.toJSONString(), headers);

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(URI.create(proxyUrl), HttpMethod.POST, entity, String.class);
        } catch (Exception e) {
            log.error("Failed to submit async-callback task via proxy", e);
            JSONObject error = new JSONObject();
            error.put("status", "error");
            error.put("message", e.getMessage());
            return error;
        }

        JSONObject result = new JSONObject();
        result.put("status", response.getStatusCode().value());
        result.put("proxy_url", proxyUrl);
        result.put("target_url", TARGET_URL);
        result.put("callback_url", CALLBACK_URL);
        result.put("proxy_mode", "CALLBACK");
        result.put("description",
                "Task enqueued in CALLBACK mode. Worker will POST to " + TARGET_URL
                        + " (takes ~2s), then POST the result back to " + CALLBACK_URL + ".");
        result.put("proxy_response", response.getBody());
        return result;
    }

    /**
     * Receives the async callback from the retask4j worker. Logs the payload
     * and headers so you can observe what the worker delivers.
     */
    @RequestMapping("/demo/callback-receiver")
    public JSONObject receiveCallback(HttpServletRequest request, @RequestBody(required = false) String body) {
        Map<String, String> headerMap = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            headerMap.put(name, request.getHeader(name));
        }

        log.info("CallbackDemo callback-receiver hit - headers={}, bodyLen={}", headerMap.size(),
                body == null ? 0 : body.length());

        JSONObject parsedBody = null;
        if (body != null && !body.isBlank()) {
            try {
                parsedBody = JSON.parseObject(body);
            } catch (Exception ignored) {
                // Not JSON - keep raw string in `raw_body`
            }
        }

        JSONObject result = new JSONObject();
        result.put("received", true);
        result.put("headers", headerMap);
        result.put("raw_body", body);
        result.put("parsed_body", parsedBody);
        result.put("received_at", System.currentTimeMillis());
        return result;
    }
}
