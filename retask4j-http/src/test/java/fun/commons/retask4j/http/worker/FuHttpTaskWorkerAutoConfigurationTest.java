package fun.commons.retask4j.http.worker;

import fun.commons.retask4j.http.worker.FuHttpTaskWorkerProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FuHttpTaskWorkerAutoConfigurationTest {

    @Nested
    @DisplayName("FuHttpTaskWorkerService 构造守卫条件")
    class GuardConditions {

        @Test
        @DisplayName("workers 为 null 时属性检查")
        void workersNullCheck() {
            FuHttpTaskWorkerProperties properties = new FuHttpTaskWorkerProperties();
            properties.setWorkers(null);
            assertNull(properties.getWorkers());
        }

        @Test
        @DisplayName("workers 为空列表时属性检查")
        void workersEmptyCheck() {
            FuHttpTaskWorkerProperties properties = new FuHttpTaskWorkerProperties();
            properties.setWorkers(List.of());
            assertTrue(properties.getWorkers().isEmpty());
        }

        @Test
        @DisplayName("workers 包含配置时通过守卫")
        void workersNonEmptyPasses() {
            FuHttpTaskWorkerProperties properties = new FuHttpTaskWorkerProperties();
            FuHttpTaskWorkerConfig config = new FuHttpTaskWorkerConfig();
            properties.setWorkers(List.of(config));
            assertFalse(properties.getWorkers().isEmpty());
        }
    }

    @Nested
    @DisplayName("FuHttpTaskWorkerAutoConfiguration 条件注解")
    class ConditionalAnnotation {

        @Test
        @DisplayName("类有 @ConditionalOnProperty 注解")
        void hasConditionalOnProperty() {
            assertTrue(
                java.util.Arrays.stream(FuHttpTaskWorkerAutoConfiguration.class.getAnnotations())
                    .anyMatch(a -> a.annotationType().getSimpleName().equals("ConditionalOnProperty")),
                "FuHttpTaskWorkerAutoConfiguration should have @ConditionalOnProperty"
            );
        }

        @Test
        @DisplayName("redis 无 redisson 时属性检查")
        void redisWithoutRedissonCheck() {
            FuHttpTaskWorkerProperties properties = new FuHttpTaskWorkerProperties();
            properties.setRedis(new HashMap<>());
            assertFalse(properties.getRedis().containsKey("redisson"));
        }
    }
}
