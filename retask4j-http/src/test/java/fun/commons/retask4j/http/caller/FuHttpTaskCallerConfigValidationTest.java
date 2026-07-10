package fun.commons.retask4j.http.caller;

import fun.commons.retask4j.core.message.FuTaskMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FuHttpTaskCallerConfigValidationTest {

    private FuHttpTaskCallerConfig createConfig() {
        FuHttpTaskCallerConfig config = new FuHttpTaskCallerConfig();
        config.setTopic("test");
        config.setPath("/test");
        return config;
    }

    @Nested
    @DisplayName("topic 验证 (TopicValidator)")
    class TopicValidation {

        @Test
        @DisplayName("null 拒绝")
        void nullTopic() {
            FuHttpTaskCallerConfig config = new FuHttpTaskCallerConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setTopic(null));
        }

        @Test
        @DisplayName("空字符串拒绝")
        void blankTopic() {
            FuHttpTaskCallerConfig config = new FuHttpTaskCallerConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setTopic(""));
            assertThrows(IllegalArgumentException.class,
                () -> config.setTopic("   "));
        }

        @Test
        @DisplayName("128 字符允许（边界值）")
        void topicAtMaxLength() {
            FuHttpTaskCallerConfig config = new FuHttpTaskCallerConfig();
            assertDoesNotThrow(() -> config.setTopic("t".repeat(128)));
        }

        @Test
        @DisplayName("129 字符拒绝")
        void topicExceedsMaxLength() {
            FuHttpTaskCallerConfig config = new FuHttpTaskCallerConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setTopic("t".repeat(129)));
        }

        @Test
        @DisplayName("包含冒号拒绝")
        void topicWithColon() {
            FuHttpTaskCallerConfig config = new FuHttpTaskCallerConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setTopic("topic:name"));
        }

        @Test
        @DisplayName("包含 { 拒绝")
        void topicWithOpenBrace() {
            FuHttpTaskCallerConfig config = new FuHttpTaskCallerConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setTopic("topic{name"));
        }

        @Test
        @DisplayName("包含 } 拒绝")
        void topicWithCloseBrace() {
            FuHttpTaskCallerConfig config = new FuHttpTaskCallerConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setTopic("topic}name"));
        }

        @Test
        @DisplayName("包含控制字符拒绝")
        void topicWithControlChar() {
            FuHttpTaskCallerConfig config = new FuHttpTaskCallerConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setTopic("topicname"));
        }
    }

    @Nested
    @DisplayName("path 验证")
    class PathValidation {

        @Test
        @DisplayName("null 拒绝")
        void nullPath() {
            FuHttpTaskCallerConfig config = new FuHttpTaskCallerConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setPath(null));
        }

        @Test
        @DisplayName("空字符串拒绝")
        void blankPath() {
            FuHttpTaskCallerConfig config = new FuHttpTaskCallerConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setPath(""));
        }

        @Test
        @DisplayName("合法路径 /proxy/push 允许")
        void validPath() {
            FuHttpTaskCallerConfig config = new FuHttpTaskCallerConfig();
            config.setPath("/proxy/push");
            assertEquals("/proxy/push", config.getPath());
        }

        @Test
        @DisplayName("不以 / 开头拒绝")
        void pathWithoutLeadingSlash() {
            FuHttpTaskCallerConfig config = new FuHttpTaskCallerConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setPath("proxy/push"));
        }

        @Test
        @DisplayName("包含特殊字符拒绝")
        void pathWithSpecialChars() {
            FuHttpTaskCallerConfig config = new FuHttpTaskCallerConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setPath("/proxy/push?query=1"));
        }
    }

    @Nested
    @DisplayName("mode 验证")
    class ModeValidation {

        @Test
        @DisplayName("三个合法模式允许")
        void validModes() {
            FuHttpTaskCallerConfig config = createConfig();
            assertDoesNotThrow(() -> config.setMode(FuTaskMode.NORMAL));
            assertDoesNotThrow(() -> config.setMode(FuTaskMode.FUNCTION));
            assertDoesNotThrow(() -> config.setMode(FuTaskMode.CALLBACK));
        }

        @Test
        @DisplayName("null rejected")
        void nullMode() {
            FuHttpTaskCallerConfig config = createConfig();
            assertThrows(IllegalArgumentException.class, () -> config.setMode(null));
        }

        @Test
        @DisplayName("非法模式拒绝")
        void invalidMode() {
            FuHttpTaskCallerConfig config = createConfig();
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> config.setMode("BATCH"));
            assertTrue(e.getMessage().contains("NORMAL, FUNCTION, or CALLBACK"));
        }
    }

    @Nested
    @DisplayName("executeExpire 验证")
    class ExecuteExpireValidation {

        @Test
        @DisplayName("1 允许（边界值）")
        void executeExpireAtMin() {
            FuHttpTaskCallerConfig config = createConfig();
            config.setExecuteExpire(1);
            assertEquals(1, config.getExecuteExpire());
        }

        @Test
        @DisplayName("0 拒绝")
        void executeExpireZero() {
            FuHttpTaskCallerConfig config = createConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setExecuteExpire(0));
        }
    }

    @Nested
    @DisplayName("resultExpire 验证")
    class ResultExpireValidation {

        @Test
        @DisplayName("0 允许")
        void resultExpireZero() {
            FuHttpTaskCallerConfig config = createConfig();
            config.setResultExpire(0);
            assertEquals(0, config.getResultExpire());
        }

        @Test
        @DisplayName("负数拒绝")
        void resultExpireNegative() {
            FuHttpTaskCallerConfig config = createConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setResultExpire(-1));
        }
    }

    @Nested
    @DisplayName("requestTimeout 验证")
    class RequestTimeoutValidation {

        @Test
        @DisplayName("1 允许")
        void requestTimeoutAtMin() {
            FuHttpTaskCallerConfig config = createConfig();
            config.setRequestTimeout(1);
            assertEquals(1, config.getRequestTimeout());
        }

        @Test
        @DisplayName("0 拒绝")
        void requestTimeoutZero() {
            FuHttpTaskCallerConfig config = createConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setRequestTimeout(0));
        }
    }

    @Nested
    @DisplayName("callbackMaxThreads 验证")
    class CallbackMaxThreadsValidation {

        @Test
        @DisplayName("1 允许")
        void callbackMaxThreadsAtMin() {
            FuHttpTaskCallerConfig config = createConfig();
            config.setCallbackMaxThreads(1);
            assertEquals(1, config.getCallbackMaxThreads());
        }

        @Test
        @DisplayName("0 拒绝")
        void callbackMaxThreadsZero() {
            FuHttpTaskCallerConfig config = createConfig();
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
            FuHttpTaskCallerConfig config = createConfig();
            config.setCallbackRetryTimes(0);
            assertEquals(0, config.getCallbackRetryTimes());
        }

        @Test
        @DisplayName("负数拒绝")
        void callbackRetryTimesNegative() {
            FuHttpTaskCallerConfig config = createConfig();
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
            FuHttpTaskCallerConfig config = createConfig();
            config.setCallbackRetryInterval(1);
            assertEquals(1, config.getCallbackRetryInterval());
        }

        @Test
        @DisplayName("0 拒绝")
        void callbackRetryIntervalZero() {
            FuHttpTaskCallerConfig config = createConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setCallbackRetryInterval(0));
        }

        @Test
        @DisplayName(">= callbackPendingTimeout 拒绝（交叉验证）")
        void callbackRetryIntervalCrossValidation() {
            FuHttpTaskCallerConfig config = createConfig();
            // default pendingTimeout=300, setting interval to 300 should fail
            assertThrows(IllegalArgumentException.class,
                () -> config.setCallbackRetryInterval(300));
        }
    }

    @Nested
    @DisplayName("callbackPendingTimeout 验证")
    class CallbackPendingTimeoutValidation {

        @Test
        @DisplayName("0 拒绝")
        void callbackPendingTimeoutZero() {
            FuHttpTaskCallerConfig config = createConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setCallbackPendingTimeout(0));
        }

        @Test
        @DisplayName("<= callbackRetryInterval 拒绝（交叉验证）")
        void callbackPendingTimeoutCrossValidation() {
            FuHttpTaskCallerConfig config = createConfig();
            // default retryInterval=60, setting pendingTimeout=60 should fail
            assertThrows(IllegalArgumentException.class,
                () -> config.setCallbackPendingTimeout(60));
        }
    }

    @Nested
    @DisplayName("strategy 验证")
    class StrategyValidation {

        @Test
        @DisplayName("null 允许")
        void nullStrategy() {
            FuHttpTaskCallerConfig config = createConfig();
            assertDoesNotThrow(() -> config.setStrategy(null));
        }

        @Test
        @DisplayName("128 字符允许")
        void strategyAtMaxLength() {
            FuHttpTaskCallerConfig config = createConfig();
            assertDoesNotThrow(() -> config.setStrategy("s".repeat(128)));
        }

        @Test
        @DisplayName("129 字符拒绝")
        void strategyExceedsMaxLength() {
            FuHttpTaskCallerConfig config = createConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setStrategy("s".repeat(129)));
        }
    }

    @Nested
    @DisplayName("maxFuncCacheSize 验证")
    class MaxFuncCacheSizeValidation {

        @Test
        @DisplayName("1 允许")
        void maxFuncCacheSizeAtMin() {
            FuHttpTaskCallerConfig config = createConfig();
            config.setMaxFuncCacheSize(1);
            assertEquals(1, config.getMaxFuncCacheSize());
        }

        @Test
        @DisplayName("0 拒绝")
        void maxFuncCacheSizeZero() {
            FuHttpTaskCallerConfig config = createConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setMaxFuncCacheSize(0));
        }
    }

    @Nested
    @DisplayName("maxDelayTime 验证")
    class MaxDelayTimeValidation {

        @Test
        @DisplayName("0 允许")
        void maxDelayTimeZero() {
            FuHttpTaskCallerConfig config = createConfig();
            config.setMaxDelayTime(0);
            assertEquals(0, config.getMaxDelayTime());
        }

        @Test
        @DisplayName("负数拒绝")
        void maxDelayTimeNegative() {
            FuHttpTaskCallerConfig config = createConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setMaxDelayTime(-1));
        }
    }

    @Nested
    @DisplayName("maxQueueDepth 验证")
    class MaxQueueDepthValidation {

        @Test
        @DisplayName("0 允许")
        void maxQueueDepthZero() {
            FuHttpTaskCallerConfig config = createConfig();
            config.setMaxQueueDepth(0);
            assertEquals(0, config.getMaxQueueDepth());
        }

        @Test
        @DisplayName("负数拒绝")
        void maxQueueDepthNegative() {
            FuHttpTaskCallerConfig config = createConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setMaxQueueDepth(-1));
        }
    }

    @Nested
    @DisplayName("headers 验证")
    class HeadersValidation {

        @Test
        @DisplayName("null 规范化为空 map")
        void nullHeadersNormalized() {
            FuHttpTaskCallerConfig config = createConfig();
            config.setHeaders(null);
            assertNotNull(config.getHeaders());
            assertTrue(config.getHeaders().isEmpty());
        }

        @Test
        @DisplayName("返回不可修改 map")
        void headersUnmodifiable() {
            FuHttpTaskCallerConfig config = createConfig();
            config.setHeaders(Map.of("X-Token", "abc"));
            assertThrows(UnsupportedOperationException.class,
                () -> config.getHeaders().put("X-New", "val"));
        }
    }

    @Nested
    @DisplayName("retryPlan 验证")
    class RetryPlanValidation {

        @Test
        @DisplayName("21 条目拒绝")
        void retryPlanExceedsMaxEntries() {
            FuHttpTaskCallerConfig config = createConfig();
            List<Integer> plan = new ArrayList<>();
            for (int i = 0; i < 21; i++) plan.add(i + 1);
            assertThrows(IllegalArgumentException.class,
                () -> config.setRetryPlan(plan));
        }

        @Test
        @DisplayName("delay=0 拒绝")
        void retryPlanZeroDelay() {
            FuHttpTaskCallerConfig config = createConfig();
            assertThrows(IllegalArgumentException.class,
                () -> config.setRetryPlan(List.of(0)));
        }

        @Test
        @DisplayName("返回不可修改列表")
        void retryPlanUnmodifiable() {
            FuHttpTaskCallerConfig config = createConfig();
            config.setRetryPlan(List.of(5, 10));
            assertThrows(UnsupportedOperationException.class,
                () -> config.getRetryPlan().add(20));
        }
    }

    @Nested
    @DisplayName("toCallConfig 验证")
    class ToCallConfigValidation {

        @Test
        @DisplayName("CALLBACK mode 无 callbackUrl 拒绝")
        void callbackModeWithoutUrl() {
            FuHttpTaskCallerConfig config = createConfig();
            config.setMode(FuTaskMode.CALLBACK);
            // callbackUrl is null by default
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> config.toCallConfig());
            assertTrue(e.getMessage().contains("callbackUrl must be set"));
        }

        @Test
        @DisplayName("default config does not throw on toCallConfig")
        void defaultConfigToCallConfig() {
            FuHttpTaskCallerConfig config = createConfig();
            assertDoesNotThrow(() -> config.toCallConfig());
        }
    }

    @Nested
    @DisplayName("deepCopy 验证")
    class DeepCopyValidation {

        @Test
        @DisplayName("deepCopy 产生独立副本")
        void deepCopyIsIndependent() {
            FuHttpTaskCallerConfig original = createConfig();
            original.setRetryPlan(List.of(5, 10));
            original.setMode(FuTaskMode.FUNCTION);

            FuHttpTaskCallerConfig copy = original.deepCopy();
            copy.setMode(FuTaskMode.NORMAL);
            copy.setRetryPlan(List.of(1, 2, 3));

            assertEquals(FuTaskMode.FUNCTION, original.getMode());
            assertEquals(2, original.getRetryPlan().size());
            assertEquals(FuTaskMode.NORMAL, copy.getMode());
            assertEquals(3, copy.getRetryPlan().size());
        }
    }
}
