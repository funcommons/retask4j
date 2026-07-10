package fun.commons.retask4j.core.config;

import fun.commons.retask4j.core.strategy.FuTaskWorkStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FuTaskWorkConfigValidationTest {

    @Nested
    @DisplayName("maxConsumeThreads 验证")
    class MaxConsumeThreadsValidation {

        @Test
        @DisplayName("1 允许（边界值）")
        void maxConsumeThreadsAtMin() {
            FuTaskWorkConfig config = new FuTaskWorkConfig("test");
            config.setMaxConsumeThreads(1);
            assertEquals(1, config.getMaxConsumeThreads());
        }

        @Test
        @DisplayName("0 拒绝")
        void maxConsumeThreadsZero() {
            FuTaskWorkConfig config = new FuTaskWorkConfig("test");
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> config.setMaxConsumeThreads(0));
            assertTrue(e.getMessage().contains("at least 1"));
        }

        @Test
        @DisplayName("负数拒绝")
        void maxConsumeThreadsNegative() {
            FuTaskWorkConfig config = new FuTaskWorkConfig("test");
            assertThrows(IllegalArgumentException.class,
                () -> config.setMaxConsumeThreads(-1));
        }
    }

    @Nested
    @DisplayName("pendingTimeout 验证")
    class PendingTimeoutValidation {

        @Test
        @DisplayName("1 允许（边界值）")
        void pendingTimeoutAtMin() {
            FuTaskWorkConfig config = new FuTaskWorkConfig("test");
            config.setPendingTimeout(1);
            assertEquals(1, config.getPendingTimeout());
        }

        @Test
        @DisplayName("0 拒绝")
        void pendingTimeoutZero() {
            FuTaskWorkConfig config = new FuTaskWorkConfig("test");
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> config.setPendingTimeout(0));
            assertTrue(e.getMessage().contains("at least 1"));
        }

        @Test
        @DisplayName("负数拒绝")
        void pendingTimeoutNegative() {
            FuTaskWorkConfig config = new FuTaskWorkConfig("test");
            assertThrows(IllegalArgumentException.class,
                () -> config.setPendingTimeout(-1));
        }
    }

    @Nested
    @DisplayName("addStrategy 验证")
    class AddStrategyValidation {

        @Test
        @DisplayName("null name 抛出 NullPointerException")
        void nullName() {
            FuTaskWorkConfig config = new FuTaskWorkConfig("test");
            assertThrows(NullPointerException.class,
                () -> config.addStrategy(null, new FuTaskWorkStrategy("x")));
        }

        @Test
        @DisplayName("空字符串 name 拒绝")
        void blankName() {
            FuTaskWorkConfig config = new FuTaskWorkConfig("test");
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> config.addStrategy("  ", new FuTaskWorkStrategy("  ")));
            assertTrue(e.getMessage().contains("blank"));
        }

        @Test
        @DisplayName("null strategy 拒绝")
        void nullStrategy() {
            FuTaskWorkConfig config = new FuTaskWorkConfig("test");
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> config.addStrategy("fast", null));
            assertTrue(e.getMessage().contains("must not be null"));
        }

        @Test
        @DisplayName("strategy name 与注册名不匹配拒绝")
        void strategyNameMismatch() {
            FuTaskWorkConfig config = new FuTaskWorkConfig("test");
            FuTaskWorkStrategy strategy = new FuTaskWorkStrategy("slow");
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> config.addStrategy("fast", strategy));
            assertTrue(e.getMessage().contains("must match"));
        }

        @Test
        @DisplayName("合法 strategy 注册成功")
        void validStrategy() {
            FuTaskWorkConfig config = new FuTaskWorkConfig("test");
            FuTaskWorkStrategy strategy = new FuTaskWorkStrategy("fast");
            FuTaskWorkConfig result = config.addStrategy("fast", strategy);

            assertSame(config, result);
            assertTrue(config.getStrategyMap().containsKey("fast"));
            assertEquals("fast", config.getStrategyMap().get("fast").getName());
        }

        @Test
        @DisplayName("strategyMap 返回不可修改视图")
        void strategyMapUnmodifiable() {
            FuTaskWorkConfig config = new FuTaskWorkConfig("test");
            assertThrows(UnsupportedOperationException.class,
                () -> config.getStrategyMap().put("hack", new FuTaskWorkStrategy("hack")));
        }
    }
}
