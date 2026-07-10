package fun.commons.retask4j.core.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;


@Getter
public class FuTaskCallConfig<R> extends FuTaskBaseConfig {

    // Retry plan (seconds) e.g. [1,3]. Retry after 1 second, retry after 3 seconds; default no retry
    @Setter(AccessLevel.NONE)
    private List<Integer> retryPlan = new ArrayList<>();

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
                if (d > MAX_EXPIRE_SECONDS) {
                    throw new IllegalArgumentException("retryPlan delay must not exceed " + MAX_EXPIRE_SECONDS + " seconds: " + d);
                }
            }
        }
        this.retryPlan = retryPlan != null ? new ArrayList<>(retryPlan) : new ArrayList<>();
    }

    // Execution expiration in seconds, default 1 day
    @Setter(AccessLevel.NONE)
    private int executeExpire = 60 * 60 * 24;

    public void setExecuteExpire(int executeExpire) {
        if (executeExpire < 1) {
            throw new IllegalArgumentException("executeExpire must be at least 1 second: " + executeExpire);
        }
        if (executeExpire > MAX_EXPIRE_SECONDS) {
            throw new IllegalArgumentException("executeExpire must not exceed " + MAX_EXPIRE_SECONDS + " seconds (30 days): " + executeExpire);
        }
        this.executeExpire = executeExpire;
    }

    // Result cache time in seconds, default 0 (no result caching)
    @Setter(AccessLevel.NONE)
    private int resultExpire = 0;

    public void setResultExpire(int resultExpire) {
        if (resultExpire < 0) {
            throw new IllegalArgumentException("resultExpire must not be negative: " + resultExpire);
        }
        if (resultExpire > MAX_EXPIRE_SECONDS) {
            throw new IllegalArgumentException("resultExpire must not exceed " + MAX_EXPIRE_SECONDS + " seconds (30 days): " + resultExpire);
        }
        this.resultExpire = resultExpire;
    }

    @Setter(AccessLevel.NONE)
    private String strategy = null;

    public void setStrategy(String strategy) {
        if (strategy != null && strategy.length() > 128) {
            throw new IllegalArgumentException("strategy must not exceed 128 characters: " + strategy.length());
        }
        this.strategy = strategy;
    }

    // Max delay time in seconds, default 86400 (24 hours), used for cache TTL calculation
    @Setter(AccessLevel.NONE)
    private int maxDelayTime = 86400;

    public void setMaxDelayTime(int maxDelayTime) {
        if (maxDelayTime < 0) {
            throw new IllegalArgumentException("maxDelayTime must not be negative: " + maxDelayTime);
        }
        if (maxDelayTime > MAX_EXPIRE_SECONDS) {
            throw new IllegalArgumentException("maxDelayTime must not exceed " + MAX_EXPIRE_SECONDS + " seconds: " + maxDelayTime);
        }
        this.maxDelayTime = maxDelayTime;
    }

    // Max retry TTL buffer in seconds, default 0 (computed from config's retryPlan), used for cache TTL calculation
    // When per-task retryPlan override is allowed, set to the maximum possible ttlBuffer value
    @Setter(AccessLevel.NONE)
    private int maxRetryTtlBuffer = 0;

    public void setMaxRetryTtlBuffer(int maxRetryTtlBuffer) {
        if (maxRetryTtlBuffer < 0) {
            throw new IllegalArgumentException("maxRetryTtlBuffer must not be negative: " + maxRetryTtlBuffer);
        }
        if (maxRetryTtlBuffer > MAX_EXPIRE_SECONDS) {
            throw new IllegalArgumentException("maxRetryTtlBuffer must not exceed " + MAX_EXPIRE_SECONDS + " seconds: " + maxRetryTtlBuffer);
        }
        this.maxRetryTtlBuffer = maxRetryTtlBuffer;
    }

    // Request timeout in seconds, default 120s
    @Setter(AccessLevel.NONE)
    private int requestTimeout = 120;

    public void setRequestTimeout(int requestTimeout) {
        if (requestTimeout < 1) {
            throw new IllegalArgumentException("requestTimeout must be at least 1 second: " + requestTimeout);
        }
        if (requestTimeout > MAX_EXPIRE_SECONDS) {
            throw new IllegalArgumentException("requestTimeout must not exceed " + MAX_EXPIRE_SECONDS + " seconds: " + requestTimeout);
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

    // Callback retry interval in seconds, default 60s
    @Setter(AccessLevel.NONE)
    private int callbackRetryInterval = 60;

    public void setCallbackRetryInterval(int callbackRetryInterval) {
        if (callbackRetryInterval < 1) {
            throw new IllegalArgumentException("callbackRetryInterval must be at least 1 second: " + callbackRetryInterval);
        }
        if (callbackRetryInterval > MAX_EXPIRE_SECONDS) {
            throw new IllegalArgumentException("callbackRetryInterval must not exceed " + MAX_EXPIRE_SECONDS + " seconds: " + callbackRetryInterval);
        }
        this.callbackRetryInterval = callbackRetryInterval;
        validateCallbackTiming();
    }

    // Callback pending timeout in seconds, default 300s; must be greater than callbackRetryInterval
    @Setter(AccessLevel.NONE)
    private int callbackPendingTimeout = 300;

    public void setCallbackPendingTimeout(int callbackPendingTimeout) {
        if (callbackPendingTimeout < 1) {
            throw new IllegalArgumentException("callbackPendingTimeout must be at least 1 second: " + callbackPendingTimeout);
        }
        if (callbackPendingTimeout > MAX_EXPIRE_SECONDS) {
            throw new IllegalArgumentException("callbackPendingTimeout must not exceed " + MAX_EXPIRE_SECONDS + " seconds: " + callbackPendingTimeout);
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

    // Maximum work deque depth before rejecting new tasks (0 = unlimited, default 0)
    // Provides back-pressure when workers can't keep up; prevents unbounded queue growth
    @Setter(AccessLevel.NONE)
    private int maxQueueDepth = 0;

    public int getMaxQueueDepth() {
        return maxQueueDepth;
    }

    public void setMaxQueueDepth(int maxQueueDepth) {
        if (maxQueueDepth < 0) {
            throw new IllegalArgumentException("maxQueueDepth must not be negative: " + maxQueueDepth);
        }
        this.maxQueueDepth = maxQueueDepth;
    }

    private final Class<R> returnCls;

    // Callback URL for CALLBACK mode (used by callback consumer)
    // Note: SSRF validation (DNS resolution + private IP check) is handled by the HTTP module's
    // SsrfValidator at config-set time and callback execution time. When using the core API directly,
    // callers should validate callback URLs before passing them here.
    @Setter(AccessLevel.NONE)
    private String callbackUrl;

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        if (callbackUrl != null && !callbackUrl.isBlank()) {
            if (callbackUrl.length() > 2048) {
                throw new IllegalArgumentException("callbackUrl must not exceed 2048 characters: " + callbackUrl.length());
            }
            // Basic format validation: must be http/https URI with a host
            try {
                java.net.URI uri = new java.net.URI(callbackUrl);
                String scheme = uri.getScheme();
                if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                    throw new IllegalArgumentException("callbackUrl must use http or https scheme");
                }
                String host = uri.getHost();
                if (host == null || host.isBlank()) {
                    throw new IllegalArgumentException("callbackUrl must have a valid host");
                }
            } catch (java.net.URISyntaxException e) {
                throw new IllegalArgumentException("callbackUrl is not a valid URI: " + e.getMessage());
            }
        }
        this.callbackUrl = callbackUrl;
    }


    public FuTaskCallConfig(String topic, Class<R> returnCls) {
        super(topic);
        java.util.Objects.requireNonNull(returnCls, "returnCls must not be null");
        this.returnCls = returnCls;
    }


}
