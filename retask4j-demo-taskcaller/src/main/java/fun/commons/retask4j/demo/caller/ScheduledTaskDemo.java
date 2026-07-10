package fun.commons.retask4j.demo.caller;

import com.alibaba.fastjson2.JSONObject;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Demo: schedule a task to execute at a future timestamp.
 *
 * <p>This controller submits a task via {@code /proxy/push} (NORMAL mode) with
 * the {@code retask4j-task-timing} header set to a future epoch-millis timestamp.
 * The retask4j caller enqueues the message into the timing sorted-set, and a
 * worker thread transfers it to the work deque once the timestamp is reached.
 *
 * <p>Body: {@code {"executeAt": <epochMillis>, "reportUrl": "<url>"}}
 *
 * <p>If {@code executeAt} is a 10-digit second timestamp, the proxy controller
 * detects it via the {@code MILLIS_THRESHOLD} heuristic and treats it as seconds.
 */
@RestController
@RequestMapping("/demo/schedule-report")
@Slf4j
public class ScheduledTaskDemo {

    private static final String PROXY_BASE_URL = "http://localhost:9400";
    private static final String PROXY_PATH = "/proxy/push";

    private final RestTemplate restTemplate = new RestTemplate();
    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneId.of("UTC"));

    @RequestMapping
    public JSONObject scheduleReport(@RequestBody(required = false) JSONObject body) {
        if (body == null) {
            body = new JSONObject();
        }
        long executeAt = body.getLongValue("executeAt", System.currentTimeMillis() + 60_000L);
        String reportUrl = body.getString("reportUrl");
        if (reportUrl == null || reportUrl.isBlank()) {
            reportUrl = "https://httpbin.org/post";
        }

        log.info("ScheduledTaskDemo invoked - scheduling report for {} at {}", reportUrl, executeAt);

        // Build the proxy URL with the target report URL appended
        String proxyUrl = PROXY_BASE_URL + PROXY_PATH + "/" + reportUrl;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Schedule execution at the given epoch-millis timestamp.
        // The proxy accepts both 13-digit ms and 10-digit s timestamps.
        headers.set("retask4j-task-timing", String.valueOf(executeAt));

        // Body sent to the proxied endpoint when it eventually executes
        JSONObject proxiedBody = new JSONObject();
        proxiedBody.put("reportUrl", reportUrl);
        proxiedBody.put("scheduledAt", executeAt);
        proxiedBody.put("note", "This will be POSTed to the reportUrl at executeAt");

        HttpEntity<String> entity = new HttpEntity<>(proxiedBody.toJSONString(), headers);

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(URI.create(proxyUrl), HttpMethod.POST, entity, String.class);
        } catch (Exception e) {
            log.error("Failed to submit scheduled-report task via proxy", e);
            JSONObject error = new JSONObject();
            error.put("status", "error");
            error.put("message", e.getMessage());
            return error;
        }

        JSONObject result = new JSONObject();
        result.put("status", response.getStatusCode().value());
        result.put("proxy_url", proxyUrl);
        result.put("target_url", reportUrl);
        result.put("execute_at_epoch_ms", executeAt);
        result.put("execute_at_iso", ISO_FORMATTER.format(Instant.ofEpochMilli(executeAt)));
        result.put("current_time_iso", ISO_FORMATTER.format(Instant.ofEpochMilli(System.currentTimeMillis())));
        result.put("delay_seconds", Math.max(0, (executeAt - System.currentTimeMillis()) / 1000));
        result.put("description",
                "Task enqueued with retask4j-task-timing=" + executeAt
                        + ". The worker will pick it up once the timestamp is reached.");
        result.put("proxy_response", response.getBody());
        return result;
    }
}
