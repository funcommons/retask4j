package fun.commons.retask4j.demo.worker.service;

import com.alibaba.fastjson2.JSONObject;

import fun.commons.retask4j.core.api.FuTaskExecutor;
import fun.commons.retask4j.core.config.FuTaskWorkConfig;
import fun.commons.retask4j.core.api.FuTaskWorker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component()
@Slf4j
public class WorkService {

    private FuTaskWorker worker;
    private final RedissonClient redissonClient;

    public WorkService(@Qualifier("redisson-client") RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @PostConstruct
    public void init() {
        FuTaskWorkConfig config = new FuTaskWorkConfig("demo");
        config.setMaxConsumeThreads(64);

        FuTaskExecutor<JSONObject,JSONObject> executor = new FuTaskExecutor<>((input) -> {
            log.debug("Consumer:{}", input.get("id"));
            input.fluentPut("ack", System.currentTimeMillis());
            try {
                if (ThreadLocalRandom.current().nextInt(100) >= 80) {
                    throw new RuntimeException("test exception");
                }
                Thread.sleep(ThreadLocalRandom.current().nextLong(5,20));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            return input;
        },JSONObject.class);

        worker = new FuTaskWorker(redissonClient, config, executor);
        worker.start();
        log.info("register demo worker");
    }

    @PreDestroy
    public void shutdown() {
        if (worker != null) {
            worker.shutdown();
        }
    }
}
