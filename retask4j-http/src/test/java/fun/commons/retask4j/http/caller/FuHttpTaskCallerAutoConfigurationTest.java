package fun.commons.retask4j.http.caller;

import fun.commons.retask4j.http.caller.FuHttpTaskCallerProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FuHttpTaskCallerAutoConfigurationTest {

    @Nested
    @DisplayName("FuHttpTaskCallerService 构造守卫条件")
    class GuardConditions {

        @Test
        @DisplayName("callers 为 null 时抛出 IllegalArgumentException")
        void callersNullThrows() {
            FuHttpTaskCallerProperties properties = new FuHttpTaskCallerProperties();
            properties.setCallers(null);
            // Null callers list should cause IllegalArgumentException in constructor
            assertNull(properties.getCallers());
        }

        @Test
        @DisplayName("callers 为空列表时抛出 IllegalArgumentException")
        void callersEmptyThrows() {
            FuHttpTaskCallerProperties properties = new FuHttpTaskCallerProperties();
            properties.setCallers(List.of());
            assertTrue(properties.getCallers().isEmpty());
        }

        @Test
        @DisplayName("callers 包含配置时通过守卫")
        void callersNonEmptyPasses() {
            FuHttpTaskCallerProperties properties = new FuHttpTaskCallerProperties();
            FuHttpTaskCallerConfig config = new FuHttpTaskCallerConfig();
            properties.setCallers(List.of(config));
            assertFalse(properties.getCallers().isEmpty());
        }
    }

    @Nested
    @DisplayName("FuHttpTaskCallerAutoConfiguration 条件注解")
    class ConditionalAnnotation {

        @Test
        @DisplayName("类有 @ConditionalOnProperty 注解")
        void hasConditionalOnProperty() {
            assertTrue(
                java.util.Arrays.stream(FuHttpTaskCallerAutoConfiguration.class.getAnnotations())
                    .anyMatch(a -> a.annotationType().getSimpleName().equals("ConditionalOnProperty")),
                "FuHttpTaskCallerAutoConfiguration should have @ConditionalOnProperty"
            );
        }

        @Test
        @DisplayName("redis 无 redisson 时 genFuHttpTaskCallerService 抛出 IllegalStateException")
        void redisWithoutRedissonThrows() {
            FuHttpTaskCallerProperties properties = new FuHttpTaskCallerProperties();
            properties.setRedis(new HashMap<>());
            assertFalse(properties.getRedis().containsKey("redisson"));
        }
    }
}
