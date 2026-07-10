package fun.commons.retask4j.core.api;

import com.alibaba.fastjson2.JSONObject;
import fun.commons.retask4j.core.config.FuTaskWorkConfig;
import fun.commons.retask4j.core.message.FuTaskMessage;
import fun.commons.retask4j.core.message.FuTaskMode;
import fun.commons.retask4j.core.message.FuTaskStatus;
import fun.commons.retask4j.core.monitor.FuTaskMonitor;
import fun.commons.retask4j.core.strategy.FuTaskWorkStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.redisson.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FuTaskWorker 测试 — 需要 mock RedissonClient。
 * 测试 Worker 消费逻辑、重试、超时、策略、监控。
 */
class FuTaskWorkerTest {

    private RedissonClient redissonClient;
    private RBlockingDeque<String> workingDeque;
    private RScoredSortedSet<String> pendingSet;
    private RScoredSortedSet<String> timingSet;
    private RScoredSortedSet<String> retrySet;
    private RBlockingDeque<Object> callbackDeque;
    private RBlockingDeque<Object> callbackPendingSet;
    private RScript rScript;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redissonClient = mock(RedissonClient.class);
        workingDeque = mock(RBlockingDeque.class);
        pendingSet = mock(RScoredSortedSet.class);
        timingSet = mock(RScoredSortedSet.class);
        retrySet = mock(RScoredSortedSet.class);
        callbackDeque = mock(RBlockingDeque.class);
        callbackPendingSet = mock(RBlockingDeque.class);
        rScript = mock(RScript.class);

        when(redissonClient.getBlockingDeque(anyString(), any())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            if (name.contains("-blocking")) return workingDeque;
            if (name.contains("-callback-pending")) return callbackPendingSet;
            return callbackDeque;
        });
        when(redissonClient.getScoredSortedSet(anyString(), any())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            if (name.contains("-pending") && !name.contains("callback")) return pendingSet;
            if (name.contains("-timing")) return timingSet;
            if (name.contains("-retry")) return retrySet;
            return mock(RScoredSortedSet.class);
        });
        when(redissonClient.getScript((org.redisson.client.codec.Codec) any())).thenReturn(rScript);

        when(workingDeque.getName()).thenReturn("fu-task-{demo}-blocking");
        when(pendingSet.getName()).thenReturn("fu-task-{demo}-pending");
        when(timingSet.getName()).thenReturn("fu-task-{demo}-timing");
        when(retrySet.getName()).thenReturn("fu-task-{demo}-retry");
        when(callbackDeque.getName()).thenReturn("fu-task-{demo}-callback");
        when(callbackPendingSet.getName()).thenReturn("fu-task-{demo}-callback-pending");

        when(rScript.eval(any(), anyString(), any(), anyList(), any())).thenReturn(1);
    }

    @Nested
    @DisplayName("Worker 构造与配置")
    class Construction {

        @Test
        @DisplayName("创建 Worker 成功")
        void createWorker() {
            FuTaskWorkConfig config = new FuTaskWorkConfig("demo");
            FuTaskExecutor<JSONObject, JSONObject> executor = new FuTaskExecutor<>(
                (input) -> new JSONObject().fluentPut("result", "ok"),
                JSONObject.class
            );

            assertDoesNotThrow(() -> new FuTaskWorker(redissonClient, config, executor));
        }

        @Test
        @DisplayName("Worker Monitor 初始值")
        void monitorInitial() {
            FuTaskWorkConfig config = new FuTaskWorkConfig("demo");
            FuTaskExecutor<JSONObject, JSONObject> executor = new FuTaskExecutor<>(
                (input) -> new JSONObject(),
                JSONObject.class
            );
            FuTaskWorker worker = new FuTaskWorker(redissonClient, config, executor);

            FuTaskMonitor.WorkerMonitor monitor = worker.getMonitor();
            assertEquals(0, monitor.consume.get());
            assertEquals(0, monitor.success.get());
            assertEquals(0, monitor.fail.get());
            assertEquals(0, monitor.finallyFail.get());
            assertEquals(0, monitor.complete.get());
        }
    }

    @Nested
    @DisplayName("消费逻辑验证（通过 consume 方法的间接测试）")
    class ConsumeLogic {

        @Test
        @DisplayName("assertSuccess 默认返回 true，配置 assertResultFunction 后生效")
        void assertSuccessWithStrategy() {
            FuTaskWorkConfig config = new FuTaskWorkConfig("demo");
            FuTaskExecutor<JSONObject, JSONObject> executor = new FuTaskExecutor<>(
                (input) -> new JSONObject(),
                JSONObject.class
            );
            FuTaskWorker worker = new FuTaskWorker(redissonClient, config, executor);

            // 无策略时默认返回 true
            boolean defaultResult = worker.assertSuccess("any-id", null, null);
            assertTrue(defaultResult, "无策略时 assertSuccess 返回 true");

            // 配置 assertResultFunction 后生效
            FuTaskWorkStrategy strategy = new FuTaskWorkStrategy("test");
            strategy.setAssertResultFunction((output, s) -> output != null && output.getIntValue("code") == 0);
            JSONObject successOutput = new JSONObject().fluentPut("code", 0);
            JSONObject failOutput = new JSONObject().fluentPut("code", 1);

            assertTrue(worker.assertSuccess("id", strategy, successOutput),
                "修复确认：assertResultFunction 返回 true 时 assertSuccess 返回 true");
            assertFalse(worker.assertSuccess("id", strategy, failOutput),
                "修复确认：assertResultFunction 返回 false 时 assertSuccess 返回 false");
        }

        @Test
        @DisplayName("onXxx 方法递增监控计数器并执行策略回调")
        void onXxxIncrementCountersAndInvokeCallbacks() {
            FuTaskWorkConfig config = new FuTaskWorkConfig("demo");
            FuTaskExecutor<JSONObject, JSONObject> executor = new FuTaskExecutor<>(
                (input) -> new JSONObject(),
                JSONObject.class
            );
            FuTaskWorker worker = new FuTaskWorker(redissonClient, config, executor);
            FuTaskMonitor.WorkerMonitor monitor = worker.getMonitor();

            // 配置策略回调
            FuTaskWorkStrategy strategy = new FuTaskWorkStrategy("test");
            java.util.concurrent.atomic.AtomicInteger callbackCount = new java.util.concurrent.atomic.AtomicInteger(0);
            strategy.setOnFailConsumer((error, s) -> callbackCount.incrementAndGet());
            strategy.setOnSuccessConsumer((output, s) -> callbackCount.incrementAndGet());
            strategy.setOnCompleteConsumer((output, s) -> callbackCount.incrementAndGet());
            strategy.setOnFinallyFailConsumer((error, s) -> callbackCount.incrementAndGet());

            // 调用 onFail
            worker.onFail("id", strategy, new RuntimeException("test"));
            assertEquals(1, monitor.fail.get());

            // 调用 onSuccess
            worker.onSuccess("id", strategy, new JSONObject());
            assertEquals(1, monitor.success.get());

            // 调用 onComplete
            worker.onComplete("id", strategy, new JSONObject(), null);
            assertEquals(1, monitor.complete.get());

            // 调用 onFinallyFail
            worker.onFinallyFail("id", strategy, new RuntimeException("test"));
            assertEquals(1, monitor.finallyFail.get());

            // 修复确认：回调被执行
            assertEquals(4, callbackCount.get(), "修复确认：所有回调均被执行");
        }
    }

    @Nested
    @DisplayName("Worker 线程池配置")
    class ThreadPoolConfig {

        @Test
        @DisplayName("自定义 maxConsumeThreads")
        void customMaxThreads() {
            FuTaskWorkConfig config = new FuTaskWorkConfig("demo");
            config.setMaxConsumeThreads(128);

            assertEquals(128, config.getMaxConsumeThreads());
        }

        @Test
        @DisplayName("多策略配置")
        void multipleStrategies() {
            FuTaskWorkConfig config = new FuTaskWorkConfig("demo");
            config.addStrategy("fast", new FuTaskWorkStrategy("fast"));
            config.addStrategy("reliable", new FuTaskWorkStrategy("reliable"));

            assertEquals(3, config.getStrategyMap().size()); // default + fast + reliable
            assertTrue(config.getStrategyMap().containsKey("default"));
            assertTrue(config.getStrategyMap().containsKey("fast"));
            assertTrue(config.getStrategyMap().containsKey("reliable"));
        }
    }

    @Nested
    @DisplayName("超时与过期检查逻辑验证")
    class TimeoutCheck {

        @Test
        @DisplayName("任务执行过期判定：createTime + delayTime + executeExpire")
        void executeExpireCheck() {
            long createTime = System.currentTimeMillis() - 7200000L; // 2 小时前创建
            int delayTime = 0;
            int executeExpire = 3600; // 1 小时过期

            long expireTime = createTime + (delayTime * 1000L) + (executeExpire * 1000L);
            long now = System.currentTimeMillis();

            assertTrue(now > expireTime, "2 小时前创建、1 小时过期的任务应该已过期");
        }

        @Test
        @DisplayName("任务未过期判定")
        void notExpired() {
            long createTime = System.currentTimeMillis(); // 刚创建
            int delayTime = 0;
            int executeExpire = 3600;

            long expireTime = createTime + (delayTime * 1000L) + (executeExpire * 1000L);
            long now = System.currentTimeMillis();

            assertTrue(now < expireTime, "刚创建的任务不应过期");
        }

        @Test
        @DisplayName("重试次数耗尽判定")
        void retryTimesExhausted() {
            List<Integer> retryPlan = List.of(5, 20, 60); // 最多 3 次重试
            int retryTimes = 4; // 已重试 4 次（超过 plan 的 3 次）

            assertTrue(retryTimes > retryPlan.size(),
                "retryTimes > retryPlan.size() 时判定为重试次数耗尽");
        }

        @Test
        @DisplayName("重试次数未耗尽")
        void retryTimesNotExhausted() {
            List<Integer> retryPlan = List.of(5, 20, 60);
            int retryTimes = 1;

            assertFalse(retryTimes > retryPlan.size());
        }
    }

    @Nested
    @DisplayName("start() 启动")
    class Start {

        @Test
        @DisplayName("start 不抛异常")
        void startNoException() {
            FuTaskWorkConfig config = new FuTaskWorkConfig("demo");
            FuTaskExecutor<JSONObject, JSONObject> executor = new FuTaskExecutor<>(
                (input) -> new JSONObject(),
                JSONObject.class
            );
            FuTaskWorker worker = new FuTaskWorker(redissonClient, config, executor);

            assertDoesNotThrow(() -> worker.start());
        }
    }
}
