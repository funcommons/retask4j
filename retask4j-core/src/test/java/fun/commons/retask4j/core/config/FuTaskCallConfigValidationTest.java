package fun.commons.retask4j.core.config;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FuTaskCallConfigValidationTest {

    private FuTaskCallConfig<JSONObject> createConfig() {
        return new FuTaskCallConfig<>("test", JSONObject.class);
    }

    @Nested
    @DisplayName("构造函数验证")
    class Constructor {

        @Test
        @DisplayName("returnCls 为 null 抛出 NullPointerException")
        void nullReturnCls() {
            assertThrows(NullPointerException.class,
                () -> new FuTaskCallConfig<>("test", null));
        }
    }

    @Nested
    @DisplayName("retryPlan 验证")
    class RetryPlanValidation {

        @Test
        @DisplayName("null 允许")
        void nullRetryPlan() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            assertDoesNotThrow(() -> config.setRetryPlan(null));
        }

        @Test
        @DisplayName("20 条目允许（边界值）")
        void retryPlanAtMaxEntries() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            List<Integer> plan = new ArrayList<>();
            for (int i = 0; i < 20; i++) plan.add(i + 1);
            assertDoesNotThrow(() -> config.setRetryPlan(plan));
            assertEquals(20, config.getRetryPlan().size());
        }

        @Test
        @DisplayName("21 条目拒绝")
        void retryPlanExceedsMaxEntries() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            List<Integer> plan = new ArrayList<>();
            for (int i = 0; i < 21; i++) plan.add(i + 1);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> config.setRetryPlan(plan));
            assertTrue(e.getMessage().contains("20"));
        }

        @Test
        @DisplayName("delay=0 拒绝")
        void retryPlanZeroDelay() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setRetryPlan(List.of(0)));
        }

        @Test
        @DisplayName("负数 delay 拒绝")
        void retryPlanNegativeDelay() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setRetryPlan(List.of(-1)));
        }

        @Test
        @DisplayName("返回不可修改列表")
        void retryPlanUnmodifiable() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            config.setRetryPlan(List.of(5, 10));
            assertThrows(UnsupportedOperationException.class,
                () -> config.getRetryPlan().add(20));
        }

        @Test
        @DisplayName("防御性拷贝：修改原始列表不影响配置")
        void retryPlanDefensiveCopy() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            List<Integer> original = new ArrayList<>(List.of(5, 10));
            config.setRetryPlan(original);
            original.add(20);
            assertEquals(2, config.getRetryPlan().size());
        }
    }

    @Nested
    @DisplayName("executeExpire 验证")
    class ExecuteExpireValidation {

        @Test
        @DisplayName("1 允许（下边界）")
        void executeExpireAtMin() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            config.setExecuteExpire(1);
            assertEquals(1, config.getExecuteExpire());
        }

        @Test
        @DisplayName("2592000 允许（上边界 = 30天）")
        void executeExpireAtMax() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            config.setExecuteExpire(2_592_000);
            assertEquals(2_592_000, config.getExecuteExpire());
        }

        @Test
        @DisplayName("0 拒绝")
        void executeExpireZero() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setExecuteExpire(0));
        }

        @Test
        @DisplayName("负数拒绝")
        void executeExpireNegative() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setExecuteExpire(-1));
        }

        @Test
        @DisplayName("2592001 拒绝（超过30天）")
        void executeExpireExceedsMax() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> config.setExecuteExpire(2_592_001));
            assertTrue(e.getMessage().contains("2592000"));
        }
    }

    @Nested
    @DisplayName("resultExpire 验证")
    class ResultExpireValidation {

        @Test
        @DisplayName("0 允许")
        void resultExpireZero() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            config.setResultExpire(0);
            assertEquals(0, config.getResultExpire());
        }

        @Test
        @DisplayName("2592000 允许（上边界）")
        void resultExpireAtMax() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            config.setResultExpire(2_592_000);
            assertEquals(2_592_000, config.getResultExpire());
        }

        @Test
        @DisplayName("负数拒绝")
        void resultExpireNegative() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setResultExpire(-1));
        }

        @Test
        @DisplayName("2592001 拒绝")
        void resultExpireExceedsMax() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setResultExpire(2_592_001));
        }
    }

    @Nested
    @DisplayName("strategy 验证")
    class StrategyValidation {

        @Test
        @DisplayName("null 允许")
        void nullStrategy() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            assertDoesNotThrow(() -> config.setStrategy(null));
        }

        @Test
        @DisplayName("128 字符允许（边界值）")
        void strategyAtMaxLength() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            assertDoesNotThrow(() -> config.setStrategy("s".repeat(128)));
        }

        @Test
        @DisplayName("129 字符拒绝")
        void strategyExceedsMaxLength() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setStrategy("s".repeat(129)));
        }
    }

    @Nested
    @DisplayName("maxDelayTime 验证")
    class MaxDelayTimeValidation {

        @Test
        @DisplayName("0 允许")
        void maxDelayTimeZero() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            config.setMaxDelayTime(0);
            assertEquals(0, config.getMaxDelayTime());
        }

        @Test
        @DisplayName("负数拒绝")
        void maxDelayTimeNegative() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setMaxDelayTime(-1));
        }
    }

    @Nested
    @DisplayName("maxRetryTtlBuffer 验证")
    class MaxRetryTtlBufferValidation {

        @Test
        @DisplayName("0 允许")
        void maxRetryTtlBufferZero() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            config.setMaxRetryTtlBuffer(0);
            assertEquals(0, config.getMaxRetryTtlBuffer());
        }

        @Test
        @DisplayName("负数拒绝")
        void maxRetryTtlBufferNegative() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setMaxRetryTtlBuffer(-1));
        }
    }

    @Nested
    @DisplayName("requestTimeout 验证")
    class RequestTimeoutValidation {

        @Test
        @DisplayName("1 允许（边界值）")
        void requestTimeoutAtMin() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            config.setRequestTimeout(1);
            assertEquals(1, config.getRequestTimeout());
        }

        @Test
        @DisplayName("0 拒绝")
        void requestTimeoutZero() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setRequestTimeout(0));
        }
    }

    @Nested
    @DisplayName("callbackMaxThreads 验证")
    class CallbackMaxThreadsValidation {

        @Test
        @DisplayName("1 允许（边界值）")
        void callbackMaxThreadsAtMin() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            config.setCallbackMaxThreads(1);
            assertEquals(1, config.getCallbackMaxThreads());
        }

        @Test
        @DisplayName("0 拒绝")
        void callbackMaxThreadsZero() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setCallbackMaxThreads(0));
        }
    }

    @Nested
    @DisplayName("callbackRetryTimes 验证")
    class CallbackRetryTimesValidation {

        @Test
        @DisplayName("0 允许")
        void callbackRetryTimesZero() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            config.setCallbackRetryTimes(0);
            assertEquals(0, config.getCallbackRetryTimes());
        }

        @Test
        @DisplayName("负数拒绝")
        void callbackRetryTimesNegative() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setCallbackRetryTimes(-1));
        }
    }

    @Nested
    @DisplayName("callbackRetryInterval 验证")
    class CallbackRetryIntervalValidation {

        @Test
        @DisplayName("1 允许（边界值）")
        void callbackRetryIntervalAtMin() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            config.setCallbackRetryInterval(1);
            assertEquals(1, config.getCallbackRetryInterval());
        }

        @Test
        @DisplayName("0 拒绝")
        void callbackRetryIntervalZero() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setCallbackRetryInterval(0));
        }

        @Test
        @DisplayName(">= callbackPendingTimeout 拒绝（交叉验证）")
        void callbackRetryIntervalNotLessThanPendingTimeout() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            // default pendingTimeout=300, retryInterval=60
            // setting retryInterval to 300 should fail
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> config.setCallbackRetryInterval(300));
            assertTrue(e.getMessage().contains("must be less than callbackPendingTimeout"));
        }
    }

    @Nested
    @DisplayName("callbackPendingTimeout 验证")
    class CallbackPendingTimeoutValidation {

        @Test
        @DisplayName("1 允许（边界值）")
        void callbackPendingTimeoutAtMin() {
            // Must set retryInterval to 0... but that's invalid.
            // So we need retryInterval < pendingTimeout. Set interval=1, timeout=2
            FuTaskCallConfig<JSONObject> config = createConfig();
            config.setCallbackRetryInterval(1);
            config.setCallbackPendingTimeout(2);
            assertEquals(2, config.getCallbackPendingTimeout());
        }

        @Test
        @DisplayName("0 拒绝")
        void callbackPendingTimeoutZero() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setCallbackPendingTimeout(0));
        }

        @Test
        @DisplayName("<= callbackRetryInterval 拒绝（交叉验证）")
        void callbackPendingTimeoutNotGreaterThanRetryInterval() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            // default retryInterval=60, setting pendingTimeout=60 should fail
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> config.setCallbackPendingTimeout(60));
            assertTrue(e.getMessage().contains("must be less than callbackPendingTimeout"));
        }
    }

    @Nested
    @DisplayName("maxFuncCacheSize 验证")
    class MaxFuncCacheSizeValidation {

        @Test
        @DisplayName("1 允许（边界值）")
        void maxFuncCacheSizeAtMin() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            config.setMaxFuncCacheSize(1);
            assertEquals(1, config.getMaxFuncCacheSize());
        }

        @Test
        @DisplayName("0 拒绝")
        void maxFuncCacheSizeZero() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setMaxFuncCacheSize(0));
        }
    }

    @Nested
    @DisplayName("maxQueueDepth 验证")
    class MaxQueueDepthValidation {

        @Test
        @DisplayName("0 允许（无限制）")
        void maxQueueDepthZero() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            config.setMaxQueueDepth(0);
            assertEquals(0, config.getMaxQueueDepth());
        }

        @Test
        @DisplayName("负数拒绝")
        void maxQueueDepthNegative() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setMaxQueueDepth(-1));
        }
    }

    @Nested
    @DisplayName("callbackUrl 验证")
    class CallbackUrlValidation {

        @Test
        @DisplayName("null 允许")
        void nullCallbackUrl() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            assertDoesNotThrow(() -> config.setCallbackUrl(null));
        }

        @Test
        @DisplayName("空字符串允许")
        void blankCallbackUrl() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            assertDoesNotThrow(() -> config.setCallbackUrl(""));
            assertDoesNotThrow(() -> config.setCallbackUrl("   "));
        }

        @Test
        @DisplayName("合法 http URL 允许")
        void validHttpUrl() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            config.setCallbackUrl("http://example.com/callback");
            assertEquals("http://example.com/callback", config.getCallbackUrl());
        }

        @Test
        @DisplayName("合法 https URL 允许")
        void validHttpsUrl() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            config.setCallbackUrl("https://example.com/callback");
            assertEquals("https://example.com/callback", config.getCallbackUrl());
        }

        @Test
        @DisplayName("ftp scheme 拒绝")
        void ftpSchemeRejected() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> config.setCallbackUrl("ftp://example.com/file"));
            assertTrue(e.getMessage().contains("http or https"));
        }

        @Test
        @DisplayName("无 host 拒绝")
        void noHostRejected() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> config.setCallbackUrl("http:///path"));
            assertTrue(e.getMessage().contains("valid host"));
        }

        @Test
        @DisplayName("非法 URI 拒绝")
        void invalidUriRejected() {
            FuTaskCallConfig<JSONObject> config = createConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setCallbackUrl("http://[invalid-ipv6"));
        }
    }
}
