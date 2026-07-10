package fun.commons.retask4j.core.config;


import fun.commons.retask4j.core.config.FuTaskBaseConfig;
import fun.commons.retask4j.core.strategy.FuTaskWorkStrategy;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


@Getter
public class FuTaskWorkConfig extends FuTaskBaseConfig {

    @Setter(AccessLevel.NONE)
    private int maxConsumeThreads = 64;

    public void setMaxConsumeThreads(int maxConsumeThreads) {
        if (maxConsumeThreads < 1) {
            throw new IllegalArgumentException("maxConsumeThreads must be at least 1: " + maxConsumeThreads);
        }
        if (maxConsumeThreads > 1024) {
            throw new IllegalArgumentException("maxConsumeThreads must not exceed 1024: " + maxConsumeThreads);
        }
        this.maxConsumeThreads = maxConsumeThreads;
    }

    @Setter(AccessLevel.NONE)
    private int pendingTimeout = 86400;

    public void setPendingTimeout(int pendingTimeout) {
        if (pendingTimeout < 1) {
            throw new IllegalArgumentException("pendingTimeout must be at least 1 second: " + pendingTimeout);
        }
        if (pendingTimeout > FuTaskBaseConfig.MAX_EXPIRE_SECONDS) {
            throw new IllegalArgumentException("pendingTimeout must not exceed " + FuTaskBaseConfig.MAX_EXPIRE_SECONDS + " seconds: " + pendingTimeout);
        }
        this.pendingTimeout = pendingTimeout;
    }

    @Setter(AccessLevel.NONE)
    private final Map<String, FuTaskWorkStrategy> strategyMap = new ConcurrentHashMap<>();

    public FuTaskWorkConfig(){
        this("default");
    }
    public FuTaskWorkConfig(String topic) {
        super(topic);
        this.strategyMap.put("default",new FuTaskWorkStrategy("default"));
    }

    public FuTaskWorkConfig addStrategy(String name, FuTaskWorkStrategy strategy){
        Objects.requireNonNull(name, "strategy name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("strategy name must not be blank");
        }
        if (strategy == null) {
            throw new IllegalArgumentException("strategy must not be null");
        }
        if (strategy.getName() == null || !strategy.getName().equals(name)) {
            throw new IllegalArgumentException("strategy name must match the registered name: expected=" + name + ", actual=" + strategy.getName());
        }
        this.strategyMap.put(name,strategy);
        return this;
    }

    public Map<String, FuTaskWorkStrategy> getStrategyMap() {
        return Collections.unmodifiableMap(strategyMap);
    }

}
