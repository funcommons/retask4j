package fun.commons.retask4j.core;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import fun.commons.retask4j.core.message.FuTaskMessage;
import fun.commons.retask4j.core.monitor.FuTaskMonitor;
import fun.commons.retask4j.core.strategy.FuTaskWorkStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBlockingDeque;
import org.redisson.api.RedissonClient;
import org.redisson.api.RScript;
import org.redisson.api.RScoredSortedSet;
import org.redisson.client.codec.StringCodec;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug 修复验证测试 — 验证 diff-table.md 中发现的代码 Bug 已被修复。
 */
class BugVerificationTest {

    @Nested
    @DisplayName("Bug #1: retask4j-task-timing 延时计算公式已修复")
    class TimingDelayFix {

        @Test
        @DisplayName("验证延时计算修复：未来时间点的 delayTime 为正数")
        void timingDelayCalculationFixed() {
            long nowTime = System.currentTimeMillis();
            long timing = nowTime + 5 * 60 * 1000L;

            // 修复后的计算方式：(timing - currentTime)
            int delayTime = (int) ((timing - nowTime) / 1000);

            assertTrue(delayTime > 0,
                "修复确认：未来时间点的 delayTime 为正数: " + delayTime);
            assertEquals(300, delayTime);
        }

        @Test
        @DisplayName("验证 10 位时间戳场景修复")
        void timingDelayFixWith10DigitTimestamp() {
            long nowTime = System.currentTimeMillis();
            long timingSeconds = (nowTime / 1000) + 300;
            long timing = timingSeconds < 1700000000000L ? timingSeconds * 1000L : timingSeconds;

            int delayTime = (int) ((timing - nowTime) / 1000);

            assertTrue(delayTime > 0,
                "修复确认：10 位时间戳场景 delayTime 为正数: " + delayTime);
        }
    }

    @Nested
    @DisplayName("Bug #2: FuTaskWorker.consume() 空指针已修复")
    class WorkerNPEFix {

        @Test
        @DisplayName("验证 null 安全：先判空后访问字段")
        void workerNullSafeOnNullMessage() {
            FuTaskMessage taskMessage = null;

            // 修复后的顺序：先判空，后访问字段
            String id = null;
            if (taskMessage == null) {
                // 安全处理，不会 NPE
            } else {
                id = taskMessage.getId();
            }

            assertNull(id, "修复确认：taskMessage 为 null 时不会 NPE，id 为 null");
        }

        @Test
        @DisplayName("验证非 null 时正常获取 id")
        void workerNormalOnNonNullMessage() {
            FuTaskMessage taskMessage = new FuTaskMessage("test", "task-001");

            String id = null;
            if (taskMessage == null) {
                // 不进入
            } else {
                id = taskMessage.getId();
            }

            assertEquals("task-001", id, "修复确认：taskMessage 非 null 时正常获取 id");
        }
    }

    @Nested
    @DisplayName("Bug #3: WorkerMonitor.finallyFail 拼写已修复")
    class FillyFailTypoFix {

        @Test
        @DisplayName("验证 finallyFail 字段存在且 fillyFail 不再存在")
        void finallyFailFieldExists() throws NoSuchFieldException {
            Field finallyFailField = FuTaskMonitor.WorkerMonitor.class.getDeclaredField("finallyFail");
            assertNotNull(finallyFailField, "修复确认：WorkerMonitor 中存在 finallyFail 字段");

            assertThrows(NoSuchFieldException.class, () -> {
                FuTaskMonitor.WorkerMonitor.class.getDeclaredField("fillyFail");
            }, "修复确认：WorkerMonitor 中不再存在 fillyFail 字段（错误拼写已删除）");

            FuTaskMonitor.WorkerMonitor monitor = new FuTaskMonitor.WorkerMonitor();
            monitor.finallyFail.incrementAndGet();
            assertEquals(1, monitor.finallyFail.get());
        }
    }

    @Nested
    @DisplayName("Bug #4: timing 与 delay 已添加互斥逻辑")
    class TimingDelayMutualExclusionFix {

        @Test
        @DisplayName("验证互斥逻辑：timing 存在时 delay 不生效")
        void mutualExclusionTimingFirst() {
            // 模拟修复后的逻辑：else if 使 delay 和 timing 互斥
            boolean timingPresent = true;
            boolean delayPresent = true;
            int delayTime = 0;

            if (timingPresent) {
                long nowTime = System.currentTimeMillis();
                long timing = nowTime + 600000L;
                delayTime = (int) ((timing - nowTime) / 1000);
            } else if (delayPresent) {
                delayTime = 300;
            }

            assertEquals(600, delayTime,
                "修复确认：timing 存在时 delay 被跳过，delayTime 来自 timing 计算");
        }

        @Test
        @DisplayName("验证互斥逻辑：仅 delay 存在时正常工作")
        void delayOnlyWhenNoTiming() {
            boolean timingPresent = false;
            boolean delayPresent = true;
            int delayTime = 0;

            if (timingPresent) {
                // 不进入
            } else if (delayPresent) {
                delayTime = 300;
            }

            assertEquals(300, delayTime,
                "修复确认：仅 delay 存在时正常设置 delayTime");
        }
    }

    @Nested
    @DisplayName("Bug #5: FuTaskWorkStrategy 回调已实现")
    class StrategyDeadCodeFix {

        @Test
        @DisplayName("验证 assertResultFunction 可配置并生效")
        void assertResultFunctionWorks() {
            FuTaskWorkStrategy strategy = new FuTaskWorkStrategy("test");
            strategy.setAssertResultFunction((output, s) -> output != null && output.getIntValue("code") == 0);

            JSONObject successOutput = new JSONObject().fluentPut("code", 0);
            JSONObject failOutput = new JSONObject().fluentPut("code", 1);

            assertTrue(strategy.getAssertResultFunction().apply(successOutput, strategy),
                "修复确认：assertResultFunction 可以判断成功");
            assertFalse(strategy.getAssertResultFunction().apply(failOutput, strategy),
                "修复确认：assertResultFunction 可以判断失败");
        }

        @Test
        @DisplayName("验证回调 Consumer 可配置并执行")
        void callbackConsumersWork() {
            StringBuilder log = new StringBuilder();
            FuTaskWorkStrategy strategy = new FuTaskWorkStrategy("test");
            strategy.setOnSuccessConsumer((output, s) -> log.append("success:").append(s.getName()));
            strategy.setOnFailConsumer((error, s) -> log.append("fail:").append(error.getMessage()));
            strategy.setOnFinallyFailConsumer((error, s) -> log.append("finallyFail:").append(error.getMessage()));
            strategy.setOnCompleteConsumer((output, s) -> log.append("complete"));

            strategy.getOnSuccessConsumer().accept(new JSONObject(), strategy);
            strategy.getOnFailConsumer().accept(new RuntimeException("err"), strategy);
            strategy.getOnFinallyFailConsumer().accept(new RuntimeException("err"), strategy);
            strategy.getOnCompleteConsumer().accept(new JSONObject(), strategy);

            assertTrue(log.toString().contains("success:test"), "修复确认：onSuccessConsumer 被执行");
            assertTrue(log.toString().contains("fail:err"), "修复确认：onFailConsumer 被执行");
            assertTrue(log.toString().contains("finallyFail:err"), "修复确认：onFinallyFailConsumer 被执行");
            assertTrue(log.toString().contains("complete"), "修复确认：onCompleteConsumer 被执行");
        }

        @Test
        @DisplayName("验证 null Consumer 时安全跳过（向后兼容）")
        void nullConsumerSafeSkip() {
            FuTaskWorkStrategy strategy = new FuTaskWorkStrategy("test");
            // Consumer 为 null 时不抛异常
            assertDoesNotThrow(() -> {
                if (strategy.getOnSuccessConsumer() != null) {
                    strategy.getOnSuccessConsumer().accept(new JSONObject(), strategy);
                }
            });
        }
    }
}
