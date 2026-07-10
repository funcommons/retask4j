package fun.commons.retask4j.core.message;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FuTaskMessageValidationTest {

    @Nested
    @DisplayName("tag 验证")
    class TagValidation {

        @Test
        @DisplayName("null tag 允许")
        void nullTag() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            assertDoesNotThrow(() -> msg.setTag(null));
            assertNull(msg.getTag());
        }

        @Test
        @DisplayName("空字符串 tag 允许")
        void emptyTag() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            assertDoesNotThrow(() -> msg.setTag(""));
            assertEquals("", msg.getTag());
        }

        @Test
        @DisplayName("128 字符 tag 允许（边界值）")
        void tagAtMaxLength() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            String tag = "a".repeat(128);
            assertDoesNotThrow(() -> msg.setTag(tag));
            assertEquals(128, msg.getTag().length());
        }

        @Test
        @DisplayName("129 字符 tag 拒绝")
        void tagExceedsMaxLength() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            String tag = "a".repeat(129);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> msg.setTag(tag));
            assertTrue(e.getMessage().contains("128"));
        }
    }

    @Nested
    @DisplayName("strategy 验证")
    class StrategyValidation {

        @Test
        @DisplayName("null strategy 允许")
        void nullStrategy() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            assertDoesNotThrow(() -> msg.setStrategy(null));
        }

        @Test
        @DisplayName("128 字符 strategy 允许（边界值）")
        void strategyAtMaxLength() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            String strategy = "s".repeat(128);
            assertDoesNotThrow(() -> msg.setStrategy(strategy));
        }

        @Test
        @DisplayName("129 字符 strategy 拒绝")
        void strategyExceedsMaxLength() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            String strategy = "s".repeat(129);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> msg.setStrategy(strategy));
            assertTrue(e.getMessage().contains("128"));
        }
    }

    @Nested
    @DisplayName("delayTime 验证")
    class DelayTimeValidation {

        @Test
        @DisplayName("0 允许")
        void zeroDelay() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            assertDoesNotThrow(() -> msg.setDelayTime(0));
            assertEquals(0, msg.getDelayTime());
        }

        @Test
        @DisplayName("正数允许")
        void positiveDelay() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            msg.setDelayTime(60);
            assertEquals(60, msg.getDelayTime());
        }

        @Test
        @DisplayName("负数拒绝")
        void negativeDelay() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> msg.setDelayTime(-1));
            assertTrue(e.getMessage().contains("delayTime must not be negative"));
        }
    }

    @Nested
    @DisplayName("retryPlan 验证")
    class RetryPlanValidation {

        @Test
        @DisplayName("null 允许")
        void nullRetryPlan() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            assertDoesNotThrow(() -> msg.setRetryPlan(null));
        }

        @Test
        @DisplayName("空列表允许")
        void emptyRetryPlan() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            assertDoesNotThrow(() -> msg.setRetryPlan(List.of()));
            assertTrue(msg.getRetryPlan().isEmpty());
        }

        @Test
        @DisplayName("20 条目允许（边界值）")
        void retryPlanAtMaxEntries() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            List<Integer> plan = new ArrayList<>();
            for (int i = 0; i < 20; i++) plan.add(i + 1);
            assertDoesNotThrow(() -> msg.setRetryPlan(plan));
            assertEquals(20, msg.getRetryPlan().size());
        }

        @Test
        @DisplayName("21 条目拒绝")
        void retryPlanExceedsMaxEntries() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            List<Integer> plan = new ArrayList<>();
            for (int i = 0; i < 21; i++) plan.add(i + 1);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> msg.setRetryPlan(plan));
            assertTrue(e.getMessage().contains("20"));
        }

        @Test
        @DisplayName("delay=0 拒绝")
        void retryPlanZeroDelay() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> msg.setRetryPlan(List.of(0)));
            assertTrue(e.getMessage().contains("at least 1 second"));
        }

        @Test
        @DisplayName("负数 delay 拒绝")
        void retryPlanNegativeDelay() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> msg.setRetryPlan(List.of(-1)));
            assertTrue(e.getMessage().contains("at least 1 second"));
        }

        @Test
        @DisplayName("setRetryPlan 返回防御性拷贝")
        void retryPlanDefensiveCopy() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            List<Integer> original = new ArrayList<>(List.of(5, 10));
            msg.setRetryPlan(original);

            original.add(20);
            assertEquals(2, msg.getRetryPlan().size());

            assertThrows(UnsupportedOperationException.class,
                () -> msg.getRetryPlan().add(30));
        }
    }

    @Nested
    @DisplayName("executeExpire 验证")
    class ExecuteExpireValidation {

        @Test
        @DisplayName("1 允许（边界值）")
        void executeExpireAtMin() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            msg.setExecuteExpire(1);
            assertEquals(1, msg.getExecuteExpire());
        }

        @Test
        @DisplayName("0 拒绝")
        void executeExpireZero() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> msg.setExecuteExpire(0));
            assertTrue(e.getMessage().contains("at least 1"));
        }

        @Test
        @DisplayName("负数拒绝")
        void executeExpireNegative() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            assertThrows(IllegalArgumentException.class,
                () -> msg.setExecuteExpire(-1));
        }
    }

    @Nested
    @DisplayName("ttlBuffer 验证")
    class TtlBufferValidation {

        @Test
        @DisplayName("0 允许")
        void ttlBufferZero() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            msg.setTtlBuffer(0);
            assertEquals(0, msg.getTtlBuffer());
        }

        @Test
        @DisplayName("负数拒绝")
        void ttlBufferNegative() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            assertThrows(IllegalArgumentException.class,
                () -> msg.setTtlBuffer(-1));
        }
    }

    @Nested
    @DisplayName("resultExpire 验证")
    class ResultExpireValidation {

        @Test
        @DisplayName("0 允许")
        void resultExpireZero() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            msg.setResultExpire(0);
            assertEquals(0, msg.getResultExpire());
        }

        @Test
        @DisplayName("负数拒绝")
        void resultExpireNegative() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            assertThrows(IllegalArgumentException.class,
                () -> msg.setResultExpire(-1));
        }
    }

    @Nested
    @DisplayName("scheduleTime 验证")
    class ScheduleTimeValidation {

        @Test
        @DisplayName("0 允许")
        void scheduleTimeZero() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            msg.setScheduleTime(0);
            assertEquals(0, msg.getScheduleTime());
        }

        @Test
        @DisplayName("负数拒绝")
        void scheduleTimeNegative() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            assertThrows(IllegalArgumentException.class,
                () -> msg.setScheduleTime(-1));
        }
    }

    @Nested
    @DisplayName("retryTimes 验证")
    class RetryTimesValidation {

        @Test
        @DisplayName("0 允许")
        void retryTimesZero() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            msg.setRetryTimes(0);
            assertEquals(0, msg.getRetryTimes());
        }

        @Test
        @DisplayName("负数拒绝")
        void retryTimesNegative() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            assertThrows(IllegalArgumentException.class,
                () -> msg.setRetryTimes(-1));
        }
    }

    @Nested
    @DisplayName("retryDelay 验证")
    class RetryDelayValidation {

        @Test
        @DisplayName("0 允许")
        void retryDelayZero() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            msg.setRetryDelay(0);
            assertEquals(0, msg.getRetryDelay());
        }

        @Test
        @DisplayName("负数拒绝")
        void retryDelayNegative() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            assertThrows(IllegalArgumentException.class,
                () -> msg.setRetryDelay(-1));
        }
    }

    @Nested
    @DisplayName("status 验证")
    class StatusValidation {

        @Test
        @DisplayName("四个合法状态")
        void validStatuses() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            assertDoesNotThrow(() -> msg.setStatus(FuTaskStatus.WAITING));
            assertDoesNotThrow(() -> msg.setStatus(FuTaskStatus.PENDING));
            assertDoesNotThrow(() -> msg.setStatus(FuTaskStatus.SUCCESS));
            assertDoesNotThrow(() -> msg.setStatus(FuTaskStatus.FAIL));
        }

        @Test
        @DisplayName("null 允许")
        void nullStatus() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            assertDoesNotThrow(() -> msg.setStatus(null));
        }

        @Test
        @DisplayName("非法状态字符串拒绝")
        void invalidStatus() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> msg.setStatus("RUNNING"));
            assertTrue(e.getMessage().contains("Invalid status value"));
        }
    }

    @Nested
    @DisplayName("error 验证")
    class ErrorValidation {

        @Test
        @DisplayName("null 允许")
        void nullError() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            assertDoesNotThrow(() -> msg.setError(null));
            assertNull(msg.getError());
        }

        @Test
        @DisplayName("4096 字符 error 允许（边界值）")
        void errorAtMaxLength() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            String error = "e".repeat(4096);
            msg.setError(error);
            assertEquals(4096, msg.getError().length());
        }

        @Test
        @DisplayName("4097 字符 error 静默截断到 4096")
        void errorTruncated() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            String error = "e".repeat(4097);
            msg.setError(error);
            assertEquals(4096, msg.getError().length());
        }
    }

    @Nested
    @DisplayName("callerId 验证")
    class CallerIdValidation {

        @Test
        @DisplayName("null 允许")
        void nullCallerId() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            assertDoesNotThrow(() -> msg.setCallerId(null));
        }

        @Test
        @DisplayName("合法 callerId 允许")
        void validCallerId() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            assertDoesNotThrow(() -> msg.setCallerId("caller-abc-123"));
            assertEquals("caller-abc-123", msg.getCallerId());
        }

        @Test
        @DisplayName("包含冒号拒绝")
        void callerIdWithColon() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            assertThrows(IllegalArgumentException.class,
                () -> msg.setCallerId("caller:123"));
        }

        @Test
        @DisplayName("包含 { 拒绝")
        void callerIdWithOpenBrace() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            assertThrows(IllegalArgumentException.class,
                () -> msg.setCallerId("caller{123"));
        }

        @Test
        @DisplayName("包含 } 拒绝")
        void callerIdWithCloseBrace() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            assertThrows(IllegalArgumentException.class,
                () -> msg.setCallerId("caller}123"));
        }
    }

    @Nested
    @DisplayName("mode 验证")
    class ModeValidation {

        @Test
        @DisplayName("三个合法模式")
        void validModes() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            assertDoesNotThrow(() -> msg.setMode(FuTaskMode.NORMAL));
            assertDoesNotThrow(() -> msg.setMode(FuTaskMode.FUNCTION));
            assertDoesNotThrow(() -> msg.setMode(FuTaskMode.CALLBACK));
        }

        @Test
        @DisplayName("null 允许")
        void nullMode() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            assertDoesNotThrow(() -> msg.setMode(null));
        }

        @Test
        @DisplayName("非法模式字符串拒绝")
        void invalidMode() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> msg.setMode("BATCH"));
            assertTrue(e.getMessage().contains("Invalid mode value"));
        }
    }

    @Nested
    @DisplayName("callbackRetryTimes 验证")
    class CallbackRetryTimesValidation {

        @Test
        @DisplayName("0 允许")
        void callbackRetryTimesZero() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            msg.setCallbackRetryTimes(0);
            assertEquals(0, msg.getCallbackRetryTimes());
        }

        @Test
        @DisplayName("负数拒绝")
        void callbackRetryTimesNegative() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            assertThrows(IllegalArgumentException.class,
                () -> msg.setCallbackRetryTimes(-1));
        }
    }

    @Nested
    @DisplayName("callbackStatus 验证")
    class CallbackStatusValidation {

        @Test
        @DisplayName("合法 callbackStatus 值：WAITING, SUCCESS, FAIL")
        void validCallbackStatuses() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            assertDoesNotThrow(() -> msg.setCallbackStatus(FuTaskStatus.WAITING));
            assertDoesNotThrow(() -> msg.setCallbackStatus(FuTaskStatus.SUCCESS));
            assertDoesNotThrow(() -> msg.setCallbackStatus(FuTaskStatus.FAIL));
        }

        @Test
        @DisplayName("PENDING 不是合法 callbackStatus")
        void pendingNotValidCallbackStatus() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            assertThrows(IllegalArgumentException.class,
                () -> msg.setCallbackStatus(FuTaskStatus.PENDING));
        }

        @Test
        @DisplayName("null 允许")
        void nullCallbackStatus() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            assertDoesNotThrow(() -> msg.setCallbackStatus(null));
        }
    }

    @Nested
    @DisplayName("callbackError 验证")
    class CallbackErrorValidation {

        @Test
        @DisplayName("4097 字符 callbackError 静默截断到 4096")
        void callbackErrorTruncated() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            String error = "e".repeat(4097);
            msg.setCallbackError(error);
            assertEquals(4096, msg.getCallbackError().length());
        }
    }

    @Nested
    @DisplayName("extInfo 验证")
    class ExtInfoValidation {

        @Test
        @DisplayName("null extInfo 规范化为空 JSONObject")
        void nullExtInfoNormalized() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            msg.setExtInfo(null);
            assertNotNull(msg.getExtInfo());
            assertTrue(msg.getExtInfo().isEmpty());
        }

        @Test
        @DisplayName("非 null extInfo 保持原值")
        void nonNullExtInfoPreserved() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            JSONObject ext = new JSONObject();
            ext.put("key", "val");
            msg.setExtInfo(ext);
            assertEquals("val", msg.getExtInfo().getString("key"));
        }
    }

    @Nested
    @DisplayName("fromStringMap 规范化")
    class FromStringMapNormalization {

        @Test
        @DisplayName("null id 返回 null")
        void nullIdReturnsNull() {
            Map<String, String> map = Map.of("topic", "test");
            assertNull(FuTaskMessage.fromStringMap(map));
        }

        @Test
        @DisplayName("null topic 返回 null")
        void nullTopicReturnsNull() {
            Map<String, String> map = Map.of("id", "task-1");
            assertNull(FuTaskMessage.fromStringMap(map));
        }

        @Test
        @DisplayName("空 topic 返回 null")
        void emptyTopicReturnsNull() {
            Map<String, String> map = Map.of("id", "task-1", "topic", "");
            assertNull(FuTaskMessage.fromStringMap(map));
        }

        @Test
        @DisplayName("负 retryDelay 规范化为 0")
        void negativeRetryDelayNormalized() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            Map<String, String> map = msg.toRequestMap();
            map.put("retryDelay", "-5");
            FuTaskMessage restored = FuTaskMessage.fromStringMap(map);
            assertEquals(0, restored.getRetryDelay());
        }

        @Test
        @DisplayName("负 scheduleTime 规范化为 0")
        void negativeScheduleTimeNormalized() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            Map<String, String> map = msg.toRequestMap();
            map.put("scheduleTime", "-1");
            FuTaskMessage restored = FuTaskMessage.fromStringMap(map);
            assertEquals(0, restored.getScheduleTime());
        }

        @Test
        @DisplayName("超长 tag 截断到 128")
        void longTagTruncated() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            Map<String, String> map = msg.toRequestMap();
            map.put("tag", "t".repeat(200));
            FuTaskMessage restored = FuTaskMessage.fromStringMap(map);
            assertEquals(128, restored.getTag().length());
        }

        @Test
        @DisplayName("超长 strategy 截断到 128")
        void longStrategyTruncated() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            Map<String, String> map = msg.toRequestMap();
            map.put("strategy", "s".repeat(200));
            FuTaskMessage restored = FuTaskMessage.fromStringMap(map);
            assertEquals(128, restored.getStrategy().length());
        }

        @Test
        @DisplayName("负 retryTimes 规范化为 0")
        void negativeRetryTimesNormalized() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            Map<String, String> map = msg.toRequestMap();
            map.put("retryTimes", "-3");
            FuTaskMessage restored = FuTaskMessage.fromStringMap(map);
            assertEquals(0, restored.getRetryTimes());
        }

        @Test
        @DisplayName("callerId 含冒号规范化为 null")
        void callerIdWithColonNormalizedToNull() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            msg.setCallerId("safe-id");
            Map<String, String> map = msg.toRequestMap();
            map.put("callerId", "bad:id");
            FuTaskMessage restored = FuTaskMessage.fromStringMap(map);
            assertNull(restored.getCallerId());
        }

        @Test
        @DisplayName("FUNCTION mode 无 callerId 降级为 NORMAL")
        void functionModeWithoutCallerIdDowngraded() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            Map<String, String> map = msg.toRequestMap();
            map.put("mode", "FUNCTION");
            // callerId not set → null → mode should be downgraded to NORMAL
            FuTaskMessage restored = FuTaskMessage.fromStringMap(map);
            assertEquals(FuTaskMode.NORMAL, restored.getMode());
        }

        @Test
        @DisplayName("CALLBACK mode 无 callerId 降级为 NORMAL")
        void callbackModeWithoutCallerIdDowngraded() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            Map<String, String> map = msg.toRequestMap();
            map.put("mode", "CALLBACK");
            FuTaskMessage restored = FuTaskMessage.fromStringMap(map);
            assertEquals(FuTaskMode.NORMAL, restored.getMode());
        }

        @Test
        @DisplayName("非法 status 规范化为 WAITING")
        void invalidStatusNormalized() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            Map<String, String> map = msg.toRequestMap();
            map.put("status", "RUNNING");
            FuTaskMessage restored = FuTaskMessage.fromStringMap(map);
            assertEquals(FuTaskStatus.WAITING, restored.getStatus());
        }

        @Test
        @DisplayName("非法 mode 规范化为 NORMAL")
        void invalidModeNormalized() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            Map<String, String> map = msg.toRequestMap();
            map.put("mode", "BATCH");
            FuTaskMessage restored = FuTaskMessage.fromStringMap(map);
            assertEquals(FuTaskMode.NORMAL, restored.getMode());
        }

        @Test
        @DisplayName("非法 callbackStatus 规范化为 WAITING")
        void invalidCallbackStatusNormalized() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            Map<String, String> map = msg.toRequestMap();
            map.put("callbackStatus", "PENDING");
            FuTaskMessage restored = FuTaskMessage.fromStringMap(map);
            assertEquals(FuTaskStatus.WAITING, restored.getCallbackStatus());
        }

        @Test
        @DisplayName("负 callbackRetryTimes 规范化为 0")
        void negativeCallbackRetryTimesNormalized() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            Map<String, String> map = msg.toRequestMap();
            map.put("callbackRetryTimes", "-2");
            FuTaskMessage restored = FuTaskMessage.fromStringMap(map);
            assertEquals(0, restored.getCallbackRetryTimes());
        }

        @Test
        @DisplayName("负 resultExpire 规范化为 0")
        void negativeResultExpireNormalized() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            Map<String, String> map = msg.toRequestMap();
            map.put("resultExpire", "-10");
            FuTaskMessage restored = FuTaskMessage.fromStringMap(map);
            assertEquals(0, restored.getResultExpire());
        }

        @Test
        @DisplayName("负 ttlBuffer 规范化为 0")
        void negativeTtlBufferNormalized() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            Map<String, String> map = msg.toRequestMap();
            map.put("ttlBuffer", "-5");
            FuTaskMessage restored = FuTaskMessage.fromStringMap(map);
            assertEquals(0, restored.getTtlBuffer());
        }

        @Test
        @DisplayName("executeExpire < 1 规范化为 3600")
        void zeroExecuteExpireNormalized() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            Map<String, String> map = msg.toRequestMap();
            map.put("executeExpire", "0");
            FuTaskMessage restored = FuTaskMessage.fromStringMap(map);
            assertEquals(3600, restored.getExecuteExpire());
        }

        @Test
        @DisplayName("retryPlan 中 < 1 的 delay 规范化为 1")
        void retryPlanEntryBelowOneNormalized() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            msg.setRetryPlan(List.of(5, 10));
            Map<String, String> map = msg.toRequestMap();
            map.put("retryPlan", "[0,5,-1]");
            FuTaskMessage restored = FuTaskMessage.fromStringMap(map);
            assertEquals(List.of(1, 5, 1), restored.getRetryPlan());
        }

        @Test
        @DisplayName("超长 error 截断到 4096")
        void longErrorTruncated() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            Map<String, String> map = msg.toRequestMap();
            map.put("error", "e".repeat(5000));
            FuTaskMessage restored = FuTaskMessage.fromStringMap(map);
            assertEquals(4096, restored.getError().length());
        }
    }
}
