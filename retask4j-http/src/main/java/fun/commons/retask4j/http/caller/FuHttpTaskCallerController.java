package fun.commons.retask4j.http.caller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import fun.commons.retask4j.core.api.FuTaskCaller;
import fun.commons.retask4j.core.config.FuTaskCallConfig;
import fun.commons.retask4j.core.message.FuTaskMessage;
import fun.commons.retask4j.core.message.FuTaskMode;
import fun.commons.retask4j.core.util.TtlUtils;
import fun.commons.retask4j.http.HttpTaskHeaders;
import fun.commons.retask4j.http.config.RegexSafetyUtils;
import fun.commons.retask4j.http.config.SsrfValidator;
import fun.commons.retask4j.http.message.HttpMessageUtils;
import fun.commons.retask4j.http.message.HttpRequestData;
import fun.commons.retask4j.http.message.HttpResponseData;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class FuHttpTaskCallerController extends FuHttpTaskBaseController {

    private static final long MAX_TIMING_DELAY_MS = 24L * 3600 * 1000;
    private static final int MAX_TASK_DELAY_SECONDS = 3600;
    private static final int MAX_ASSERT_RESPONSE_BYTES = 4096;
    // Max retry delay for per-request header override. Intentionally more restrictive than
    // FuTaskCallConfig.MAX_EXPIRE_SECONDS (30 days) because the header is an untrusted input surface.
    private static final int MAX_HEADER_RETRY_DELAY_SECONDS = 86400;
    // Threshold to distinguish second (10-digit) vs millisecond (13-digit) timestamps.
    // 1_700_000_000 ≈ Nov 2023 in seconds; values below this are seconds, above are milliseconds.
    private static final long MILLIS_THRESHOLD = 1_700_000_000_000L;

    private final FuHttpTaskCallerConfig httpTaskCallerConfig;

    @Getter
    private final FuTaskCaller<HttpResponseData> caller;

    public FuTaskCaller<HttpResponseData> getCaller() {
        return caller;
    }
    private final String topic;
    private final String path;

    public FuHttpTaskCallerController(RedissonClient redissonClient, RestTemplate restTemplate, FuHttpTaskCallerConfig httpTaskCallerConfig) {
        // Deep copy to isolate from mutable Spring-bound config
        this.httpTaskCallerConfig = httpTaskCallerConfig.deepCopy();
        this.topic = this.httpTaskCallerConfig.getTopic();
        this.path =  this.httpTaskCallerConfig.getPath();

        log.info("Create FuHttpTaskCallerController {}", this.httpTaskCallerConfig.getTopic());
        FuTaskCallConfig<HttpResponseData> taskCallConfig = this.httpTaskCallerConfig.toCallConfig();

        caller = new FuTaskCaller<>(redissonClient, taskCallConfig,new FuHttpTaskCallback(restTemplate , this.httpTaskCallerConfig));
        caller.start();
    }

    public void request(HttpServletRequest request, HttpServletResponse response) throws IOException {

        // Package request data
        HttpRequestData httpRequestData;
        try {
            httpRequestData = packagingRequestData(request);
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Request packaging failed: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid target URL");
            return;
        }
        if (httpRequestData == null) {
            log.error("HttpRequestData is null");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid request data");
            return;
        }

        JSONObject requestTemp = null;

        FuTaskMessage taskMessage = caller.newTaskMessage(null);
        HttpHeaders headers = httpRequestData.getHeaders();

        if (headers.containsKey(HttpTaskHeaders.RETRY_PLAN)) {
            String retryPlan = headers.getFirst(HttpTaskHeaders.RETRY_PLAN);
            try {
                List<Integer> parsedPlan = JSONArray.parseArray(retryPlan, Integer.class);
                if (parsedPlan != null && parsedPlan.size() > 20) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, HttpTaskHeaders.RETRY_PLAN + " must not exceed 20 entries");
                    return;
                }
                if (parsedPlan != null && parsedPlan.stream().anyMatch(Objects::isNull)) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, HttpTaskHeaders.RETRY_PLAN + " must not contain null values");
                    return;
                }
                if (parsedPlan != null && parsedPlan.stream().anyMatch(d -> d < 1 || d > MAX_HEADER_RETRY_DELAY_SECONDS)) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, HttpTaskHeaders.RETRY_PLAN + " delay must be 1~" + MAX_HEADER_RETRY_DELAY_SECONDS + " seconds");
                    return;
                }
                taskMessage.setRetryPlan(parsedPlan);
                taskMessage.setTtlBuffer(TtlUtils.computeTtlBuffer(parsedPlan, taskMessage.getExecuteExpire()));
            } catch (Exception e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid " + HttpTaskHeaders.RETRY_PLAN + " value");
                return;
            }
        }

        // retask4j-task-timing Scheduled message, timestamp e.g. 1737077674000
        // Supports 13-digit millisecond timestamps and 10-digit second timestamps, precision is seconds
        // Earlier than current time means immediate execution.
        // Does not support more than 24 hours
        if (headers.containsKey(HttpTaskHeaders.TASK_TIMING)) {
            long nowTime = System.currentTimeMillis();
            String timingStr = headers.getFirst(HttpTaskHeaders.TASK_TIMING);
            long timing;
            try {
                timing = Long.parseLong(timingStr);
            } catch (NumberFormatException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid " + HttpTaskHeaders.TASK_TIMING + " value");
                return;
            }
            timing = timing < MILLIS_THRESHOLD ? timing * 1000L : timing;
            if (timing > nowTime + MAX_TIMING_DELAY_MS){
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid timing , don't support more than 24 hours");
                return;
            }
            if (timing > nowTime){
                taskMessage.setDelayTime((int) Math.ceil((timing - nowTime) / 1000.0));
            }
        }

        // retask4j-task-delay Delayed message, time in seconds. Value range 1~3600. Does not support more than 24 hours
        // Cannot coexist with retask4j-task-timing
        else if (headers.containsKey(HttpTaskHeaders.TASK_DELAY)) {
            String delayStr = headers.getFirst(HttpTaskHeaders.TASK_DELAY);
            int delay;
            try {
                delay = Integer.parseInt(delayStr);
            } catch (NumberFormatException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid " + HttpTaskHeaders.TASK_DELAY + " value");
                return;
            }
            if (delay < 1 || delay > MAX_TASK_DELAY_SECONDS){
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid retask4j-task-delay , only support 1 ~ " + MAX_TASK_DELAY_SECONDS + " seconds");
                return;
            }
            taskMessage.setDelayTime(delay);
        }

        if (headers.containsKey(HttpTaskHeaders.ASSERT_RESPONSE)) {
            String assertStr = headers.getFirst(HttpTaskHeaders.ASSERT_RESPONSE);
            if (assertStr != null && assertStr.length() > MAX_ASSERT_RESPONSE_BYTES) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, HttpTaskHeaders.ASSERT_RESPONSE + " must not exceed " + MAX_ASSERT_RESPONSE_BYTES + " characters");
                return;
            }
            if (!JSON.isValidObject(assertStr)){
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid " + HttpTaskHeaders.ASSERT_RESPONSE + ", must be json object");
                return;
            }
            JSONObject assertJson = JSONObject.parseObject(assertStr);
            // Validate regex patterns at submission time to prevent ReDoS patterns from reaching worker queue
            try {
                if (assertJson.containsKey("textBodyMatch")) {
                    String pattern = assertJson.getString("textBodyMatch");
                    if (pattern != null) {
                        RegexSafetyUtils.compileSafePattern(pattern);
                    }
                }
                if (assertJson.containsKey("headerMatch")) {
                    JSONObject hm = assertJson.getJSONObject("headerMatch");
                    if (hm != null) {
                        for (Object v : hm.values()) {
                            if (v instanceof String) RegexSafetyUtils.compileSafePattern((String) v);
                        }
                    }
                }
                if (assertJson.containsKey("jsonPathMatch")) {
                    JSONObject jm = assertJson.getJSONObject("jsonPathMatch");
                    if (jm != null) {
                        for (Object v : jm.values()) {
                            if (v instanceof String) RegexSafetyUtils.compileSafePattern((String) v);
                        }
                    }
                }
            } catch (IllegalArgumentException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid regex in " + HttpTaskHeaders.ASSERT_RESPONSE + ": " + e.getMessage());
                return;
            }
            taskMessage.getExtInfo().put("assert-response", assertJson);
        }

        // Extract callback URL before stripping retask4j-* headers (needed for CALLBACK mode dispatch below)
        String callbackUrl = httpTaskCallerConfig.getCallbackUrl();
        if (headers.containsKey(HttpTaskHeaders.CALLBACK_URL)) {
            String headerUrl = headers.getFirst(HttpTaskHeaders.CALLBACK_URL);
            if (headerUrl != null && !headerUrl.isBlank()) {
                if (headerUrl.length() > 2048) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, HttpTaskHeaders.CALLBACK_URL + " must not exceed 2048 characters");
                    return;
                }
                callbackUrl = headerUrl;
            }
        }

        // Strip any remaining retask4j-* headers to prevent internal headers leaking to workers
        headers.keySet().removeIf(key -> key != null && key.toLowerCase().startsWith(HttpTaskHeaders.PREFIX));

        requestTemp = JSONObject.from(httpRequestData);
        taskMessage.setInput(requestTemp);

        if (FuTaskMode.NORMAL.equals(httpTaskCallerConfig.getMode())) {
            pushTaskMessage(taskMessage, request, response);
        }else if (FuTaskMode.FUNCTION.equals(httpTaskCallerConfig.getMode())) {
            funcAsyncTaskMessage(taskMessage, request, response);
        }else if (FuTaskMode.CALLBACK.equals(httpTaskCallerConfig.getMode())) {
            // Validate callbackUrl only in CALLBACK mode to avoid unnecessary DNS resolution
            if (callbackUrl != null && !callbackUrl.equals(httpTaskCallerConfig.getCallbackUrl())) {
                try {
                    SsrfValidator.validateUri(callbackUrl, HttpTaskHeaders.CALLBACK_URL);
                } catch (IllegalArgumentException e) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid callback URL");
                    return;
                }
            }
            if (StringUtils.isBlank(callbackUrl)) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "callbackUrl is required in CALLBACK mode");
                return;
            }
            callbackTaskMessage(taskMessage,callbackUrl, request, response);
        }else {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unrecognized mode: " + httpTaskCallerConfig.getMode());
        }

    }

    public void funcAsyncTaskMessage(FuTaskMessage taskMessage, HttpServletRequest request, HttpServletResponse response) throws IOException {

        // Start asynchronous processing
        AsyncContext asyncContext = request.startAsync();
        asyncContext.setTimeout(httpTaskCallerConfig.getRequestTimeout() * 1000L);

        // Guard to prevent concurrent writes to the response from timeout/error listener and future completion
        java.util.concurrent.atomic.AtomicBoolean responseClaimed = new java.util.concurrent.atomic.AtomicBoolean(false);

        CompletableFuture<HttpResponseData> future;
        try {
            if (httpTaskCallerConfig.isBatch()){
                future = caller.funcAsyncBatch(taskMessage);
            }else {
                future = caller.funcAsync(taskMessage);
            }
        } catch (Exception e) {
            log.error("Failed to create async future", e);
            if (responseClaimed.compareAndSet(false, true) && !response.isCommitted()) {
                writeApiError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Task submission failed");
            }
            try {
                asyncContext.complete();
            } catch (IllegalStateException ex) {
                log.debug("Async context already completed");
            }
            return;
        }

        // Pass future and response guard to listener so timeout/error can cancel it
        asyncContext.addListener(new FuHttpTaskCallerAsyncListener(future, responseClaimed));

        future.whenComplete((result, throwable) -> {
            try {
                if (responseClaimed.compareAndSet(false, true)) {
                    if (throwable != null) {
                        log.error("Exception in function complete", throwable);
                        if (!response.isCommitted()) {
                            writeApiError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Task execution failed");
                        }
                    } else {
                        log.debug("Async operation successful, flushing response");
                        if (!response.isCommitted()) {
                            HttpMessageUtils.flushToHttpResponse(result, response);
                        } else {
                            log.warn("Response already committed, cannot write async result");
                        }
                    }
                }
            } catch (IOException e) {
                log.error("Error flushing response", e);
            } finally {
                try {
                    asyncContext.complete();
                } catch (IllegalStateException e) {
                    log.debug("Async context already completed");
                }
            }
        });
    }

    public void pushTaskMessage(FuTaskMessage taskMessage, HttpServletRequest request, HttpServletResponse response) throws IOException {
        int result;
        if (httpTaskCallerConfig.isBatch()){
            result = caller.sendTaskMessageBatch(taskMessage);
        }else {
            result = caller.sendTaskMessage(taskMessage);
        }
        if (result <= 0) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Failed to enqueue task");
            return;
        }
        writeApiSuccess(response, JSONObject.of("taskId",taskMessage.getId()));
    }

    public void callbackTaskMessage(FuTaskMessage taskMessage,String callbackUrl, HttpServletRequest request, HttpServletResponse response) throws IOException {

        taskMessage.getExtInfo().put("callback", callbackUrl);

        int result;
        if (httpTaskCallerConfig.isBatch()){
            result = caller.sendCallbackMessageBatch(taskMessage);
        }else {
            result = caller.sendCallbackMessage(taskMessage);
        }
        if (result <= 0) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Failed to enqueue task");
            return;
        }
        writeApiSuccess(response, JSONObject.of("taskId",taskMessage.getId()));
    }

    private static final int MAX_URL_LENGTH = 8192;

    private HttpRequestData  packagingRequestData(HttpServletRequest request) throws IOException {
        // Package request data

        String url = request.getRequestURI();
        String prefix = request.getServletContext().getContextPath() + path + "/";
        String uri = url.startsWith(prefix) ? url.substring(prefix.length()) : url;

        // Supports both **/democaller/http/www.abc.com/test and **/democaller/http://www.abc.com/test formats
        if (uri.toLowerCase().startsWith("http/") || uri.toLowerCase().startsWith("https/")) {
            uri = uri.replaceFirst("^(?i)(https?)/", "$1://");
        }

        if ( !uri.startsWith("/") && !uri.toLowerCase().startsWith("http://") && !uri.toLowerCase().startsWith("https://")) {
            uri = "/" + uri;
        }

        String queryString = request.getQueryString();
        UriComponents targetUrl = UriComponentsBuilder.fromUriString(uri)
                .query(queryString != null ? queryString : "").build();

        if (targetUrl.toUriString().length() > MAX_URL_LENGTH) {
            throw new IOException("Target URL exceeds maximum length of " + MAX_URL_LENGTH + " characters");
        }

        // Validate scheme before any DNS resolution to prevent unexpected lookups
        String scheme = targetUrl.getScheme();
        if (scheme != null && !scheme.isEmpty() && !HttpTaskHeaders.HTTP_SCHEMES.contains(scheme.toLowerCase())) {
            throw new IOException("Unsupported URL scheme: " + scheme + " (only http/https allowed)");
        }

        // SSRF prevention with IP pinning: resolve hostname, validate IPs are non-private,
        // and reconstruct URL with pinned IP to prevent DNS rebinding (TOCTOU) attacks.
        // The authoritative SSRF check with IP pinning happens on the worker side at callback
        // execution time, but we also pin here to close the window between validation and use.
        String targetHost = targetUrl.getHost();
        String pinnedHost = null;
        if (targetHost != null && !targetHost.isEmpty()) {
            try {
                pinnedHost = SsrfValidator.resolveAndValidate(targetHost, "Target host");
            } catch (IllegalArgumentException e) {
                throw new IOException(e.getMessage());
            }
        }

        // Package request data
        HttpRequestData httpRequestData = HttpMessageUtils.convertRequestData(request);

        // Replace URL: if we resolved and pinned an IP, reconstruct URL with IP instead of hostname
        if (pinnedHost != null) {
            String effectiveScheme = (scheme != null && !scheme.isEmpty()) ? scheme : "http";
            // Handle IPv6 addresses in URL (must be bracketed)
            String hostForUrl = pinnedHost.contains(":") ? "[" + pinnedHost + "]" : pinnedHost;
            try {
                java.net.URI reconstructedUri = new java.net.URI(
                    effectiveScheme,
                    targetUrl.getUserInfo(),
                    hostForUrl,
                    targetUrl.getPort(),
                    targetUrl.getPath(),
                    targetUrl.getQuery(),
                    targetUrl.getFragment()
                );
                httpRequestData.setUrl(reconstructedUri.toString());
            } catch (java.net.URISyntaxException e) {
                throw new IOException("Failed to reconstruct URI with pinned host: " + e.getMessage());
            }
        } else {
            httpRequestData.setUrl(targetUrl.toUriString());
        }

        // Update headers
        if (Objects.nonNull(httpTaskCallerConfig.getHeaders())) {
            httpTaskCallerConfig.getHeaders().forEach((k, v) -> {
                if (StringUtils.isNotBlank(v)) {
                    httpRequestData.getHeaders().set(k, v);
                }
                else {
                    httpRequestData.getHeaders().remove(k);
                }
            });
        }

        if (scheme != null && HttpTaskHeaders.HTTP_SCHEMES.contains(scheme.toLowerCase())) {
            log.debug("remote http call");
            String host = targetUrl.getHost();
            if (host != null) {
                // Set Host header to original hostname (not pinned IP) for virtual hosting
                httpRequestData.getHeaders().set("host", host + (targetUrl.getPort() > 0 ? ":" + targetUrl.getPort() : ""));
            }
        } else {
            log.debug("local http call");
        }

        return httpRequestData;

    }

    public void shutdown() {
        caller.shutdown();
    }

}



