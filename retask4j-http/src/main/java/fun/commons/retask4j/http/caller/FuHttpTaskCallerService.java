package fun.commons.retask4j.http.caller;


import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import fun.commons.retask4j.core.api.FuTaskCaller;
import fun.commons.retask4j.http.config.HttpClientFactory;
import fun.commons.retask4j.http.config.RedissonUtils;
import fun.commons.retask4j.http.message.HttpResponseData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.*;

@Slf4j
public class FuHttpTaskCallerService implements  DisposableBean {

    private static final RequestMethod[] ALL_METHODS = {RequestMethod.GET,RequestMethod.POST,RequestMethod.PUT,RequestMethod.DELETE,RequestMethod.PATCH};

    @Getter
    private final FuHttpTaskCallerProperties callerProperties;

    @Getter
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Getter
    private final RedissonClient redissonClient;

    private final boolean ownsRedissonClient;

    private final List<RequestMappingInfo> requestMappingInfoList = new ArrayList<>();

    public List<RequestMappingInfo> getRequestMappingInfoList() {
        return Collections.unmodifiableList(requestMappingInfoList);
    }

    @Getter
    private final RestTemplate restTemplate;

    private final HttpClientFactory.HttpClientHolder httpClientHolder;

    @Getter
    private final Map<String,FuHttpTaskCallerController> controllerMap;

    public List<fun.commons.retask4j.core.api.FuTaskCaller<?>> getCallers() {
        List<fun.commons.retask4j.core.api.FuTaskCaller<?>> out = new ArrayList<>(controllerMap.size());
        for (FuHttpTaskCallerController c : controllerMap.values()) {
            out.add(c.getCaller());
        }
        return out;
    }

    public FuHttpTaskCallerService(FuHttpTaskCallerProperties properties , RequestMappingHandlerMapping requestMappingHandlerMapping) throws  Exception {
        this(properties, requestMappingHandlerMapping, createRedissonClient(properties), true);
    }

    public FuHttpTaskCallerService(FuHttpTaskCallerProperties properties , RequestMappingHandlerMapping requestMappingHandlerMapping, RedissonClient redissonClient) throws  Exception {
        this(properties, requestMappingHandlerMapping, redissonClient, false);
    }

    private FuHttpTaskCallerService(FuHttpTaskCallerProperties properties, RequestMappingHandlerMapping requestMappingHandlerMapping, RedissonClient redissonClient, boolean ownsRedissonClient) throws Exception {

        log.info("Initialize FuHttpTaskCallerService");

        this.callerProperties = properties;
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
        this.redissonClient = redissonClient;
        this.ownsRedissonClient = ownsRedissonClient;

        Map<String, FuHttpTaskCallerController> map = new HashMap<>();
        List<FuHttpTaskCallerConfig> callers = new ArrayList<>(properties.getCallers());
        if (callers == null || callers.isEmpty()) {
            throw new IllegalArgumentException("retask4j.http.callers must not be null or empty");
        }
        for (FuHttpTaskCallerConfig c : callers) {
            if (c == null) {
                throw new IllegalArgumentException("retask4j.http.callers must not contain null elements");
            }
        }

        // Derive HTTP client timeouts from caller configs (callback HTTP calls should respect requestTimeout)
        int maxConnectTimeout = 60_000;
        int maxReadTimeout = 120_000;
        for (FuHttpTaskCallerConfig callerConfig : callers) {
            int configTimeout = (int) Math.min(callerConfig.getRequestTimeout() * 1000L, Integer.MAX_VALUE);
            if (configTimeout > maxReadTimeout) {
                maxReadTimeout = configTimeout;
            }
        }
        HttpClientFactory.HttpClientHolder holder = HttpClientFactory.create(maxConnectTimeout, maxReadTimeout);
        this.httpClientHolder = holder;
        this.restTemplate = holder.getRestTemplate();

        FuHttpTaskCallerController lastController = null;
        Set<String> seenTopics = new HashSet<>();
        try {
            for (FuHttpTaskCallerConfig callerConfig : callers) {

                String path = callerConfig.getPath();
                String callerTopic = callerConfig.getTopic();
                if (seenTopics.contains(callerTopic)) {
                    throw new IllegalArgumentException("Duplicate caller topic: " + callerTopic + ". Each caller must have a unique topic to avoid duplicate task processing.");
                }
                seenTopics.add(callerTopic);
                lastController = new FuHttpTaskCallerController(redissonClient, restTemplate, callerConfig);
                RequestMappingInfo requestMappingInfo = RequestMappingInfo.paths(path + "/**").methods(ALL_METHODS).build();
                Method methodRequest = FuHttpTaskCallerController.class.getDeclaredMethod("request", HttpServletRequest.class, HttpServletResponse.class);
                requestMappingHandlerMapping.registerMapping(requestMappingInfo, lastController, methodRequest);
                log.info("register caller controller {} for topic {}", path + "/**", callerConfig.getTopic());
                requestMappingInfoList.add(requestMappingInfo);
                if (map.containsKey(path)) {
                    throw new IllegalArgumentException("Duplicate caller path: " + path + ". Each caller must have a unique path.");
                }
                map.put(path, lastController);
            }
        } catch (Exception e) {
            // Clean up partially-created controllers to prevent thread/connection leaks
            for (FuHttpTaskCallerController controller : map.values()) {
                try {
                    controller.shutdown();
                } catch (Exception ex) {
                    log.warn("Error shutting down partially-created controller", ex);
                }
            }
            // The last controller may have been created but not yet added to map
            if (lastController != null && !map.containsValue(lastController)) {
                try {
                    lastController.shutdown();
                } catch (Exception ex) {
                    log.warn("Error shutting down last partially-created controller", ex);
                }
            }
            for (RequestMappingInfo info : requestMappingInfoList) {
                try {
                    requestMappingHandlerMapping.unregisterMapping(info);
                } catch (Throwable ex) {
                    log.warn("Error unregistering partially-created mapping", ex);
                }
            }
            try {
                httpClientHolder.close();
            } catch (Exception ex) {
                log.warn("Error closing HttpClientHolder after constructor failure", ex);
            }
            if (ownsRedissonClient) {
                try { redissonClient.shutdown(); } catch (Exception ex) { log.warn("Error shutting down RedissonClient after constructor failure", ex); }
            }
            throw e;
        }
        this.controllerMap = Collections.unmodifiableMap(map);

    }

    private final java.util.concurrent.atomic.AtomicBoolean destroyed = new java.util.concurrent.atomic.AtomicBoolean(false);

    @Override
    public void destroy()  {
        if (!destroyed.compareAndSet(false, true)) return;
        for (RequestMappingInfo requestMappingInfo : requestMappingInfoList) {
            try {
                requestMappingHandlerMapping.unregisterMapping(requestMappingInfo);
                log.info("Unregistered caller controller mapping");
            } catch (Throwable e) {
                log.warn("Error unregistering caller controller mapping:", e);
            }
        }
        for (FuHttpTaskCallerController controller : controllerMap.values()) {
            try {
                controller.shutdown();
            } catch (Exception e) {
                log.warn("Error shutting down caller controller:", e);
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

    public static RedissonClient createRedissonClient(FuHttpTaskCallerProperties properties) throws Exception {
        return RedissonUtils.createRedissonClient(properties.getRedis());
    }

}
