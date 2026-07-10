package fun.commons.retask4j.http.worker;


import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import fun.commons.retask4j.core.exception.FuTaskAssertionException;
import fun.commons.retask4j.core.api.FuTaskExecutor;
import fun.commons.retask4j.core.config.FuTaskWorkConfig;
import fun.commons.retask4j.core.api.FuTaskWorker;
import fun.commons.retask4j.http.HttpTaskHeaders;
import fun.commons.retask4j.http.config.HttpClientFactory;
import fun.commons.retask4j.http.config.SsrfValidator;
import fun.commons.retask4j.http.config.RedissonUtils;
import fun.commons.retask4j.http.message.HttpMessageUtils;
import fun.commons.retask4j.http.message.HttpRequestData;
import fun.commons.retask4j.http.message.HttpResponseData;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class FuHttpTaskWorkerService implements DisposableBean {

    private final FuHttpTaskWorkerProperties workerProperties;
    private final WebApplicationContext webApplicationContext;

    private final RedissonClient redissonClient;

    private final boolean ownsRedissonClient;

    private final RestTemplate restTemplate;

    private final HttpClientFactory.HttpClientHolder httpClientHolder;

    private final List<FuTaskWorker> workerList = new ArrayList<>();

    public FuHttpTaskWorkerService(FuHttpTaskWorkerProperties properties, WebApplicationContext webApplicationContext) throws Exception {
        this(properties, webApplicationContext, createRedissonClient(properties), true);
    }

    public FuHttpTaskWorkerService(FuHttpTaskWorkerProperties properties, WebApplicationContext webApplicationContext, RedissonClient redissonClient) throws Exception {
        this(properties, webApplicationContext, redissonClient, false);
    }

    private FuHttpTaskWorkerService(FuHttpTaskWorkerProperties properties, WebApplicationContext webApplicationContext, RedissonClient redissonClient, boolean ownsRedissonClient) throws Exception {

        log.info("Initialize FuHttpTaskWorkerService");

        this.workerProperties = properties;
        this.webApplicationContext = webApplicationContext;
        this.redissonClient = redissonClient;
        this.ownsRedissonClient = ownsRedissonClient;

        List<FuHttpTaskWorkerConfig> workers = new ArrayList<>(properties.getWorkers());
        if (workers == null || workers.isEmpty()) {
            throw new IllegalArgumentException("retask4j.http.workers must not be null or empty");
        }
        for (FuHttpTaskWorkerConfig w : workers) {
            if (w == null) {
                throw new IllegalArgumentException("retask4j.http.workers must not contain null elements");
            }
        }

        int connectTimeout = workers.stream()
                .mapToInt(FuHttpTaskWorkerConfig::getConnectTimeout)
                .max().orElse(FuHttpTaskWorkerConfig.DEFAULT_CONNECT_TIMEOUT_MS);
        int readTimeout = workers.stream()
                .mapToInt(FuHttpTaskWorkerConfig::getReadTimeout)
                .max().orElse(FuHttpTaskWorkerConfig.DEFAULT_READ_TIMEOUT_MS);

        HttpClientFactory.HttpClientHolder holder = HttpClientFactory.create(connectTimeout, readTimeout);
        this.httpClientHolder = holder;
        this.restTemplate = holder.getRestTemplate();

        try {
            Set<String> seenTopics = new HashSet<>();
            for (FuHttpTaskWorkerConfig workerConfig : workers) {
                // Deep copy to isolate from mutable Spring-bound config
                FuHttpTaskWorkerConfig configCopy = workerConfig.deepCopy();

                String workerTopic = configCopy.getTopic();
                if (seenTopics.contains(workerTopic)) {
                    throw new IllegalArgumentException("Duplicate worker topic: " + workerTopic + ". Each worker must have a unique topic to avoid duplicate task execution.");
                }
                seenTopics.add(workerTopic);

                log.info("register worker server for topic {}", configCopy.getTopic());
                FuTaskWorkConfig fuTaskWorkConfig = configCopy.toWorkConfig();

                FuTaskExecutor<HttpRequestData, HttpResponseData> executor = new FuTaskExecutor<>((requestData, extInfo) -> {
                    try {
                        return doExecute(configCopy, requestData, extInfo);
                    } catch (FuTaskAssertionException e) {
                        throw e;
                    } catch (IllegalArgumentException e) {
                        throw e; // Non-retryable: SSRF/validation failure
                    } catch (java.net.URISyntaxException e) {
                        throw new IllegalArgumentException(e); // Non-retryable: malformed URL
                    } catch (Exception e) {
                        throw new RuntimeException(e.getMessage(), e);
                    }
                }, HttpRequestData.class);
                FuTaskWorker worker = new FuTaskWorker(redissonClient, fuTaskWorkConfig, executor);
                worker.start();
                workerList.add(worker);

            }
        } catch (Exception e) {
            // Clean up partially-created workers to prevent thread/connection leaks
            for (FuTaskWorker worker : workerList) {
                try {
                    worker.shutdown();
                } catch (Exception ex) {
                    log.warn("Error shutting down partially-created worker", ex);
                }
            }
            try {
                holder.close();
            } catch (Exception ex) {
                log.warn("Error closing HttpClientHolder after constructor failure", ex);
            }
            if (ownsRedissonClient) {
                try { redissonClient.shutdown(); } catch (Exception ex) { log.warn("Error shutting down RedissonClient after constructor failure", ex); }
            }
            throw e;
        }

    }

    private HttpResponseData doExecute(FuHttpTaskWorkerConfig config, HttpRequestData requestData, JSONObject extInfo) throws Exception {

        String url = requestData.getUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Task message has null/blank URL, discarding");
        }
        String method = requestData.getMethod();
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("Task message has null/blank method, discarding");
        }

        FuHttpTaskWorkerConfig.RouteConfig route = null;
        FuHttpTaskWorkerConfig.RouteConfig wildcardRoute = null;
        for (FuHttpTaskWorkerConfig.RouteConfig item : config.getRoutes()) {
            Pattern compiledPath = item.getCompiledPath();
            if (compiledPath == null) {
                if (wildcardRoute == null) wildcardRoute = item;
            } else if (compiledPath.matcher(url).matches()) {
                route = item;
                break;
            }
        }
        if (route == null) {
            route = wildcardRoute != null ? wildcardRoute : new FuHttpTaskWorkerConfig.RouteConfig();
        }

        // If assert-response override is needed, create a lightweight copy preserving compiledPath
        if (extInfo != null && extInfo.containsKey("assert-response")){
            FuHttpTaskWorkerConfig.RouteConfig copy = route.copy();
            // Start from the route's existing assertions, then merge per-request overrides
            FuHttpTaskWorkerConfig.AssertsConfig assertConfig = copy.getAssertResponse() != null
                    ? copy.getAssertResponse().copy() : new FuHttpTaskWorkerConfig.AssertsConfig();
            JSONObject assertJson = extInfo.getJSONObject("assert-response");
            if (assertJson != null) {
                if (assertJson.containsKey("statusIn")) {
                    assertConfig.setStatusIn(assertJson.getList("statusIn", Integer.class));
                }
                if (assertJson.containsKey("textBodyMatch")) {
                    assertConfig.setTextBodyMatch(assertJson.getString("textBodyMatch"));
                }
                if (assertJson.containsKey("headerMatch")) {
                    JSONObject headerMatchJson = assertJson.getJSONObject("headerMatch");
                    if (headerMatchJson != null) {
                        assertConfig.setHeaderMatch(headerMatchJson.to(new com.alibaba.fastjson2.TypeReference<Map<String, String>>() {}));
                    }
                }
                if (assertJson.containsKey("jsonPathMatch")) {
                    JSONObject jsonPathMatchJson = assertJson.getJSONObject("jsonPathMatch");
                    if (jsonPathMatchJson != null) {
                        assertConfig.setJsonPathMatch(jsonPathMatchJson.to(new com.alibaba.fastjson2.TypeReference<Map<String, String>>() {}));
                    }
                }
            }
            copy.setAssertResponse(assertConfig);
            route = copy;
        }

        requestData = rewriteRequestData(route, requestData);


        UriComponents targetUrl = UriComponentsBuilder.fromUriString(requestData.getUrl()).build();
        HttpResponseData httpResponseData;

        if (targetUrl.getScheme() != null && HttpTaskHeaders.HTTP_SCHEMES.contains(targetUrl.getScheme())) {
            String targetHost = targetUrl.getHost();
            if (targetHost != null && !targetHost.isEmpty()) {
                try {
                    // Resolve and validate, then pin the connection to the validated IP to prevent DNS rebinding
                    String validatedIp = SsrfValidator.resolveAndValidate(targetHost, "Target host");
                    // IPv6 addresses must be bracketed in URLs
                    String hostForUrl = validatedIp.contains(":") ? "[" + validatedIp + "]" : validatedIp;
                    // Reconstruct URI with validated IP instead of string replacement
                    // to avoid issues with userinfo, multiple host occurrences, or regex metacharacters
                    java.net.URI originalUri = new java.net.URI(requestData.getUrl());
                    String ipUrl = new java.net.URI(
                            originalUri.getScheme(),
                            originalUri.getUserInfo(),
                            hostForUrl,
                            originalUri.getPort(),
                            originalUri.getPath(),
                            originalUri.getQuery(),
                            originalUri.getFragment()
                    ).toString();
                    int port = targetUrl.getPort();
                    requestData.setUrl(ipUrl);
                    // Preserve caller-set Host header for virtual hosting; only set if not already present
                    String existingHost = requestData.getHeaders().getFirst("Host");
                    if (existingHost == null || existingHost.isBlank()) {
                        requestData.getHeaders().set("Host", targetHost + (port > 0 ? ":" + port : ""));
                    }
                } catch (IllegalArgumentException e) {
                    return HttpResponseData.error(403, "Target URL validation failed", "ssrf-blocked");
                } catch (java.net.URISyntaxException e) {
                    return HttpResponseData.error(400, "Malformed target URL", e.getMessage());
                }
            }
            if (config.isEnableRemote()) {
                httpResponseData = HttpMessageUtils.restTemplateExecute(restTemplate, requestData);
                httpResponseData = rewriteResponseData(route, httpResponseData);

            } else {
                httpResponseData = HttpResponseData.error(403, "worker is not remote call mode", url);
            }
        } else {
            if (config.isEnableLocal()) {
                // Validate that local invocation URLs are actually local paths
                String localUrl = requestData.getUrl();
                if (localUrl != null && !localUrl.isEmpty() && !localUrl.startsWith("/")) {
                    return HttpResponseData.error(403, "Local invocation URL must start with /", url);
                }
                if (localUrl != null && localUrl.startsWith("//")) {
                    return HttpResponseData.error(403, "Protocol-relative URLs not allowed for local invocation", url);
                }
                try {

                    httpResponseData = FuHttpLocalInvoker.localHttpInvoke(webApplicationContext, requestData);
                    httpResponseData = rewriteResponseData(route, httpResponseData);

                }catch (Exception e){
                    log.error("local http invoke error", e);
                    throw e;
                }

            } else {
                httpResponseData = HttpResponseData.error(403, "worker is not local call mode", url);
            }
        }

        return httpResponseData;
    }

    private HttpRequestData rewriteRequestData(FuHttpTaskWorkerConfig.RouteConfig route, HttpRequestData requestData) {

        if (route == null) {
            return requestData;
        }

        HttpRequestData copy = requestData.clone();

        String url = copy.getUrl();

        String redirect = route.getRedirect();
        Pattern compiledPath = route.getCompiledPath();
        if (StringUtils.isNotBlank(redirect) && compiledPath != null) {
            Matcher m = compiledPath.matcher(url);
            if (m.matches()) {
                String result = redirect;
                // Replace from highest to lowest to prevent &1 from matching inside &10, &11, etc.
                for (int i = m.groupCount(); i >= 0; i--) {
                    result = result.replace("&" + i, Matcher.quoteReplacement(StringUtils.defaultIfBlank(m.group(i), "")));
                }
                // Re-validate SSRF after group substitution — the substituted URL may differ from the config-level redirect
                if (result.contains("://")) {
                    SsrfValidator.validateUri(result, "redirect-after-substitution");
                }
                copy.setUrl(result);
                log.debug("redirect url:{} >> {}", url, result);
            }
        }

        if (route.getRewriteRequestHeaders() != null) {
            route.getRewriteRequestHeaders().forEach((k, v) -> {
                if (StringUtils.isNotBlank(v)) {
                    copy.getHeaders().set(k, v);
                    log.debug("rewrite request header:{} >> {}", k, v);
                } else {
                    copy.getHeaders().remove(k);
                    log.debug("remove request header:{}", k);
                }
            });
        }

        return copy;

    }

    private HttpResponseData rewriteResponseData(FuHttpTaskWorkerConfig.RouteConfig route, HttpResponseData responseData) {
        if (route == null) {
            return responseData;
        }

        FuHttpTaskWorkerConfig.AssertsConfig assertResponse = route.getAssertResponse();


        if (assertResponse != null) {

            List<Integer> statusIn = assertResponse.getStatusIn();
            if (statusIn != null && !statusIn.isEmpty()) {
                if (!statusIn.contains(responseData.getStatus())) {
                    throw new FuTaskAssertionException("status error:" + responseData.getStatus() + "," + responseData.getReason());

                }
            }

            String textBodyMatch = assertResponse.getTextBodyMatch();
            Pattern compiledTextBodyMatch = assertResponse.getCompiledTextBodyMatch();
            if (compiledTextBodyMatch != null) {
                String body = responseData.bodyText();
                // Limit matching to first 10K chars to prevent ReDoS on large responses
                if (body.length() > 10240) {
                    body = body.substring(0, 10240);
                }
                if (!compiledTextBodyMatch.matcher(body).matches()) {
                    throw new FuTaskAssertionException("body match error:" + textBodyMatch);
                }
            }

            Map<String, String> jsonKeyMatch = assertResponse.getJsonPathMatch();
            Map<String, Pattern> compiledJsonKeyMatch = assertResponse.getCompiledJsonPathMatch();
            if (compiledJsonKeyMatch != null && !compiledJsonKeyMatch.isEmpty()) {
                JSONObject body;
                try {
                    body = JSONObject.from(responseData.bodyBytes());
                } catch (Exception e) {
                    throw new FuTaskAssertionException("response body is not a valid JSON object, cannot apply jsonKeyMatch");
                }
                for (Map.Entry<String, Pattern> entry : compiledJsonKeyMatch.entrySet()) {
                    String key = entry.getKey();
                    Pattern pattern = entry.getValue();
                    String bodyValue = body.getString(key);
                    if (bodyValue == null || !pattern.matcher(bodyValue).matches()) {
                        throw new FuTaskAssertionException("json key match error:" + key + " >> " + pattern.pattern());
                    }
                }
            }

            Map<String, Pattern> compiledHeaderMatch = assertResponse.getCompiledHeaderMatch();
            if (compiledHeaderMatch != null && !compiledHeaderMatch.isEmpty()) {
                HttpHeaders responseHeaders = responseData.getHeaders();
                for (Map.Entry<String, Pattern> entry : compiledHeaderMatch.entrySet()) {
                    String headerName = entry.getKey();
                    Pattern pattern = entry.getValue();
                    String actualValue = responseHeaders.getFirst(headerName);
                    if (actualValue == null || !pattern.matcher(actualValue).matches()) {
                        throw new FuTaskAssertionException("header match error:" + headerName + " >> " + pattern.pattern());
                    }
                }
            }

        }

        Map<String, String> rewriteResponseHeaders = route.getRewriteResponseHeaders();

        if (rewriteResponseHeaders != null) {
            // Clone headers before rewriting to avoid mutating the original response
            responseData.setHeaders(new HttpHeaders(responseData.getHeaders()));
            rewriteResponseHeaders.forEach((k, v) -> {
                if (StringUtils.isNotBlank(v)) {
                    responseData.getHeaders().set(k, v);
                    log.debug("rewrite response header:{} >> {}", k, v);
                } else {
                    responseData.getHeaders().remove(k);
                    log.debug("remove response header:{}", k);
                }
            });
        }

        return responseData;

    }


    private final java.util.concurrent.atomic.AtomicBoolean destroyed = new java.util.concurrent.atomic.AtomicBoolean(false);

    @Override
    public void destroy() {
        if (!destroyed.compareAndSet(false, true)) return;
        log.info("Stopping the workers");
        for (FuTaskWorker worker : workerList) {
            try {
                worker.shutdown();
            } catch (Exception e) {
                log.warn("Error shutting down worker", e);
            }
        }
        try {
            httpClientHolder.close();
        } catch (Exception e) {
            log.warn("Error closing HttpClientHolder", e);
        }
        if (ownsRedissonClient) {
            redissonClient.shutdown();
        }
    }

    public static RedissonClient createRedissonClient(FuHttpTaskWorkerProperties properties) throws Exception {
        return RedissonUtils.createRedissonClient(properties.getRedis());
    }

}
