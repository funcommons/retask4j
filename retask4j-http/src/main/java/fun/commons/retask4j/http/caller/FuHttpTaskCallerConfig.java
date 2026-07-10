package fun.commons.retask4j.http.caller;

import fun.commons.retask4j.core.config.FuTaskBaseConfig;
import fun.commons.retask4j.core.config.FuTaskCallConfig;
import fun.commons.retask4j.core.message.FuTaskMode;
import fun.commons.retask4j.http.config.HttpHeaderUtils;
import fun.commons.retask4j.http.config.SsrfValidator;
import fun.commons.retask4j.http.config.TopicValidator;
import fun.commons.retask4j.http.message.HttpResponseData;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.*;


@Getter
public class FuHttpTaskCallerConfig {

    @NotBlank
    @Setter(AccessLevel.NONE)
    private String topic ;

    public void setTopic(String topic) {
        TopicValidator.validate(topic);
        this.topic = topic;
    }

    @NotBlank
    @Setter(AccessLevel.NONE)
    private String path;

    public void setPath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        if (path.length() > 512) {
            throw new IllegalArgumentException("path must not exceed 512 characters: " + path.length());
        }
        if (!path.matches("^/[\\w/-]+$")) {
            throw new IllegalArgumentException("path must match /^/[\\w/-]+$/");
        }
        this.path = path;
    }

    // Retry plan (seconds) e.g. [1,3]: retry after 1 second, then 3 seconds. Default: no retry
    @Setter(AccessLevel.NONE)
    private List<Integer> retryPlan = new ArrayList<>(Arrays.asList(60,120,300,600,3600));

    public List<Integer> getRetryPlan() {
        return Collections.unmodifiableList(retryPlan);
    }

    public void setRetryPlan(List<Integer> retryPlan) {
        if (retryPlan != null) {
            if (retryPlan.size() > 20) {
                throw new IllegalArgumentException("retryPlan must not exceed 20 entries: " + retryPlan.size());
            }
            for (Integer d : retryPlan) {
                if (d == null) {
                    throw new IllegalArgumentException("retryPlan must not contain null elements");
                }
                if (d < 1) {
                    throw new IllegalArgumentException("retryPlan delay must be at least 1 second: " + d);
                }
                if (d > FuTaskBaseConfig.MAX_EXPIRE_SECONDS) {
                    throw new IllegalArgumentException("retryPlan delay must not exceed " + FuTaskBaseConfig.MAX_EXPIRE_SECONDS + " seconds: " + d);
                }
            }
        }
        this.retryPlan = retryPlan != null ? new ArrayList<>(retryPlan) : new ArrayList<>();
    }

    // Execution expiration (seconds), default 1 day
    @Setter(AccessLevel.NONE)
    private int executeExpire = 60 * 60 * 24;

    public void setExecuteExpire(int executeExpire) {
        if (executeExpire < 1) {
            throw new IllegalArgumentException("executeExpire must be at least 1 second: " + executeExpire);
        }
        if (executeExpire > FuTaskBaseConfig.MAX_EXPIRE_SECONDS) {
            throw new IllegalArgumentException("executeExpire must not exceed " + FuTaskBaseConfig.MAX_EXPIRE_SECONDS + " seconds: " + executeExpire);
        }
        this.executeExpire = executeExpire;
    }

    // Result cache time (seconds), default 1 hour
    @Setter(AccessLevel.NONE)
    private int resultExpire = 3600;

    public void setResultExpire(int resultExpire) {
        if (resultExpire < 0) {
            throw new IllegalArgumentException("resultExpire must not be negative: " + resultExpire);
        }
        if (resultExpire > FuTaskBaseConfig.MAX_EXPIRE_SECONDS) {
            throw new IllegalArgumentException("resultExpire must not exceed " + FuTaskBaseConfig.MAX_EXPIRE_SECONDS + " seconds: " + resultExpire);
        }
        this.resultExpire = resultExpire;
    }

    // Request timeout (seconds), default 120s
    @Setter(AccessLevel.NONE)
    private int requestTimeout = 120;

    public void setRequestTimeout(int requestTimeout) {
        if (requestTimeout < 1) {
            throw new IllegalArgumentException("requestTimeout must be at least 1 second: " + requestTimeout);
        }
        if (requestTimeout > FuTaskBaseConfig.MAX_EXPIRE_SECONDS) {
            throw new IllegalArgumentException("requestTimeout must not exceed " + FuTaskBaseConfig.MAX_EXPIRE_SECONDS + " seconds: " + requestTimeout);
        }
        this.requestTimeout = requestTimeout;
    }

    // Callback max threads, default 64
    @Setter(AccessLevel.NONE)
    private int callbackMaxThreads = 64;

    public void setCallbackMaxThreads(int callbackMaxThreads) {
        if (callbackMaxThreads < 1) {
            throw new IllegalArgumentException("callbackMaxThreads must be at least 1: " + callbackMaxThreads);
        }
        if (callbackMaxThreads > 1024) {
            throw new IllegalArgumentException("callbackMaxThreads must not exceed 1024: " + callbackMaxThreads);
        }
        this.callbackMaxThreads = callbackMaxThreads;
    }

    // Callback retry times, default 3
    @Setter(AccessLevel.NONE)
    private int callbackRetryTimes = 3;

    public void setCallbackRetryTimes(int callbackRetryTimes) {
        if (callbackRetryTimes < 0) {
            throw new IllegalArgumentException("callbackRetryTimes must not be negative: " + callbackRetryTimes);
        }
        if (callbackRetryTimes > 100) {
            throw new IllegalArgumentException("callbackRetryTimes must not exceed 100: " + callbackRetryTimes);
        }
        this.callbackRetryTimes = callbackRetryTimes;
    }

    // Callback retry interval (seconds), default 60s
    @Setter(AccessLevel.NONE)
    private int callbackRetryInterval = 60;

    // Callback pending timeout (seconds), default 300s, must be greater than callbackRetryInterval
    @Setter(AccessLevel.NONE)
    private int callbackPendingTimeout = 300;

    public void setCallbackRetryInterval(int callbackRetryInterval) {
        if (callbackRetryInterval < 1) {
            throw new IllegalArgumentException("callbackRetryInterval must be at least 1 second: " + callbackRetryInterval);
        }
        if (callbackRetryInterval > FuTaskBaseConfig.MAX_EXPIRE_SECONDS) {
            throw new IllegalArgumentException("callbackRetryInterval must not exceed " + FuTaskBaseConfig.MAX_EXPIRE_SECONDS + " seconds: " + callbackRetryInterval);
        }
        this.callbackRetryInterval = callbackRetryInterval;
        validateCallbackTiming();
    }

    public void setCallbackPendingTimeout(int callbackPendingTimeout) {
        if (callbackPendingTimeout < 1) {
            throw new IllegalArgumentException("callbackPendingTimeout must be at least 1 second: " + callbackPendingTimeout);
        }
        if (callbackPendingTimeout > FuTaskBaseConfig.MAX_EXPIRE_SECONDS) {
            throw new IllegalArgumentException("callbackPendingTimeout must not exceed " + FuTaskBaseConfig.MAX_EXPIRE_SECONDS + " seconds: " + callbackPendingTimeout);
        }
        this.callbackPendingTimeout = callbackPendingTimeout;
        validateCallbackTiming();
    }

    private void validateCallbackTiming() {
        if (this.callbackRetryInterval >= this.callbackPendingTimeout) {
            throw new IllegalArgumentException(
                "callbackRetryInterval (" + this.callbackRetryInterval + ") must be less than callbackPendingTimeout (" + this.callbackPendingTimeout + ")");
        }
    }


    @Setter(AccessLevel.NONE)
    private Map<String,String> headers = new HashMap<>();

    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    public void setHeaders(Map<String, String> headers) {
        Map<String, String> copy = headers != null ? new HashMap<>(headers) : new HashMap<>();
        copy.forEach(HttpHeaderUtils::validateNoCrlf);
        this.headers = copy;
    }

    @Setter(AccessLevel.NONE)
    private String mode = FuTaskMode.NORMAL;

    public void setMode(String mode) {
        if (mode == null || mode.isBlank()) {
            throw new IllegalArgumentException("mode must not be null or blank");
        }
        if (!Set.of(FuTaskMode.NORMAL, FuTaskMode.FUNCTION, FuTaskMode.CALLBACK).contains(mode)) {
            throw new IllegalArgumentException("mode must be NORMAL, FUNCTION, or CALLBACK");
        }
        this.mode = mode;
    }

    /* Valid when mode is "CALLBACK".
    On task completion, POST to callback URL with application/json data.
    Data body:
        {"id":"task id",
        "output":"{ response data }",
        "status":"SUCCESS",
        "mode":"CALLBACK",
        "resultExpire":"expiration time (seconds)",
        "completeTime":"completion time",
        "executeTime":"execution time (seconds)"}
    */
    @Setter(AccessLevel.NONE)
    private String callbackUrl = null;

    public void setCallbackUrl(String callbackUrl) {
        if (callbackUrl != null && !callbackUrl.isBlank()) {
            if (callbackUrl.length() > 2048) {
                throw new IllegalArgumentException("callbackUrl must not exceed 2048 characters: " + callbackUrl.length());
            }
            try {
                SsrfValidator.validateUri(callbackUrl, "callbackUrl");
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(e.getMessage());
            }
        }
        this.callbackUrl = callbackUrl;
    }

    @Setter(AccessLevel.NONE)
    private String strategy = null;

    public void setStrategy(String strategy) {
        if (strategy != null && strategy.length() > 128) {
            throw new IllegalArgumentException("strategy must not exceed 128 characters: " + strategy.length());
        }
        this.strategy = strategy;
    }

    @Setter(AccessLevel.NONE)
    private boolean batch = true;

    public boolean isBatch() {
        return batch;
    }

    public void setBatch(boolean batch) {
        this.batch = batch;
    }

    // Maximum in-flight FUNCTION-mode futures cached (Guava cache maximumSize), default 100000
    @Setter(AccessLevel.NONE)
    private int maxFuncCacheSize = 100000;

    public void setMaxFuncCacheSize(int maxFuncCacheSize) {
        if (maxFuncCacheSize < 1) {
            throw new IllegalArgumentException("maxFuncCacheSize must be at least 1: " + maxFuncCacheSize);
        }
        if (maxFuncCacheSize > 10_000_000) {
            throw new IllegalArgumentException("maxFuncCacheSize must not exceed 10000000: " + maxFuncCacheSize);
        }
        this.maxFuncCacheSize = maxFuncCacheSize;
    }

    // Max delay time (seconds), default 86400 (24 hours), used for cache TTL calculation
    @Setter(AccessLevel.NONE)
    private int maxDelayTime = 86400;

    public void setMaxDelayTime(int maxDelayTime) {
        if (maxDelayTime < 0) {
            throw new IllegalArgumentException("maxDelayTime must not be negative: " + maxDelayTime);
        }
        if (maxDelayTime > FuTaskBaseConfig.MAX_EXPIRE_SECONDS) {
            throw new IllegalArgumentException("maxDelayTime must not exceed " + FuTaskBaseConfig.MAX_EXPIRE_SECONDS + " seconds: " + maxDelayTime);
        }
        this.maxDelayTime = maxDelayTime;
    }

    // Maximum work deque depth before rejecting new tasks (0 = unlimited, default 0)
    @Setter(AccessLevel.NONE)
    private int maxQueueDepth = 0;

    public void setMaxQueueDepth(int maxQueueDepth) {
        if (maxQueueDepth < 0) {
            throw new IllegalArgumentException("maxQueueDepth must not be negative: " + maxQueueDepth);
        }
        this.maxQueueDepth = maxQueueDepth;
    }

    public FuTaskCallConfig<HttpResponseData> toCallConfig() {
        if (FuTaskMode.CALLBACK.equals(mode) && (callbackUrl == null || callbackUrl.isBlank())) {
            throw new IllegalArgumentException("callbackUrl must be set when mode is CALLBACK");
        }
        FuTaskCallConfig<HttpResponseData> config =
            new FuTaskCallConfig<>(topic, HttpResponseData.class);
        config.setResultExpire(resultExpire);
        config.setRetryPlan(retryPlan);
        config.setRequestTimeout(requestTimeout);
        config.setExecuteExpire(executeExpire);
        config.setStrategy(strategy);
        config.setMaxDelayTime(maxDelayTime);
        // Account for the larger of: config-level retryPlan (max 20 entries) or
        // per-task retryPlan override via retask4j-retry-plan header (max 20 entries).
        // Each entry contributes (delay + executeExpire) to the TTL buffer.
        // Per-request header delays are capped at MAX_HEADER_RETRY_DELAY_SECONDS (86400).
        int maxRetryEntries = Math.max(retryPlan != null ? retryPlan.size() : 0, 20);
        long configMaxDelay = retryPlan != null ? retryPlan.stream().mapToLong(d -> d).max().orElse(0L) : 0L;
        long maxDelay = Math.max(configMaxDelay, 86400L);
        long worstCase = (long) maxRetryEntries * (maxDelay + (long) executeExpire);
        // Cap at MAX_EXPIRE_SECONDS — this is a cache TTL hint, not an actual Redis TTL.
        // The downstream cache TTL in FuTaskCaller is already capped at MAX_EXPIRE_SECONDS.
        long capped = Math.min(worstCase, FuTaskBaseConfig.MAX_EXPIRE_SECONDS);
        int worstCaseTtlBuffer = capped > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) capped;
        config.setMaxRetryTtlBuffer(worstCaseTtlBuffer);
        // Set retryInterval before pendingTimeout to avoid cross-validation failure:
        // callbackPendingTimeout setter validates callbackRetryInterval < callbackPendingTimeout,
        // so callbackRetryInterval must be set first (otherwise the default of 60 could exceed pendingTimeout)
        config.setCallbackRetryInterval(callbackRetryInterval);
        config.setCallbackPendingTimeout(callbackPendingTimeout);
        config.setCallbackMaxThreads(callbackMaxThreads);
        config.setCallbackRetryTimes(callbackRetryTimes);
        config.setMaxFuncCacheSize(maxFuncCacheSize);
        config.setMaxQueueDepth(maxQueueDepth);
        config.setCallbackUrl(callbackUrl);
        return config;
    }

    public FuHttpTaskCallerConfig deepCopy() {
        FuHttpTaskCallerConfig c = new FuHttpTaskCallerConfig();
        c.setTopic(this.topic);
        c.setPath(this.path);
        c.setRetryPlan(new ArrayList<>(this.retryPlan));
        c.setExecuteExpire(this.executeExpire);
        c.setResultExpire(this.resultExpire);
        c.setRequestTimeout(this.requestTimeout);
        c.setCallbackMaxThreads(this.callbackMaxThreads);
        c.setCallbackRetryTimes(this.callbackRetryTimes);
        // Set retryInterval before pendingTimeout to avoid cross-validation failure:
        // callbackPendingTimeout setter validates callbackRetryInterval < callbackPendingTimeout,
        // so callbackRetryInterval must be set first (otherwise the default of 60 could exceed pendingTimeout)
        c.setCallbackRetryInterval(this.callbackRetryInterval);
        c.setCallbackPendingTimeout(this.callbackPendingTimeout);
        c.setHeaders(this.headers);
        c.setMode(this.mode);
        // Bypass SSRF re-validation: URL was already validated when originally set
        c.callbackUrl = this.callbackUrl;
        c.setStrategy(this.strategy);
        c.setBatch(this.batch);
        c.setMaxFuncCacheSize(this.maxFuncCacheSize);
        c.setMaxDelayTime(this.maxDelayTime);
        c.setMaxQueueDepth(this.maxQueueDepth);
        return c;
    }

}
