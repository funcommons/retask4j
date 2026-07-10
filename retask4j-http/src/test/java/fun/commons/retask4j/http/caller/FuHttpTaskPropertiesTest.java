package fun.commons.retask4j.http.caller;

import fun.commons.retask4j.core.message.FuTaskMode;
import fun.commons.retask4j.http.worker.FuHttpTaskWorkerProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FuHttpTaskCallerProperties 和 FuHttpTaskWorkerProperties 测试。
 */
class FuHttpTaskPropertiesTest {

    @Nested
    @DisplayName("Caller Properties")
    class CallerProperties {

        @Test
        @DisplayName("默认值")
        void defaults() {
            FuHttpTaskCallerProperties props = new FuHttpTaskCallerProperties();

            assertNotNull(props.getRedis());
            assertTrue(props.getRedis().isEmpty());
            assertNotNull(props.getCallers());
            assertTrue(props.getCallers().isEmpty());
        }

        @Test
        @DisplayName("配置前缀为 retask4j.http")
        void configurationPrefix() {
            ConfigurationProperties annotation = FuHttpTaskCallerProperties.class
                .getAnnotation(ConfigurationProperties.class);
            assertNotNull(annotation);
            assertEquals("retask4j.http", annotation.prefix());
        }
    }

    @Nested
    @DisplayName("Worker Properties")
    class WorkerProperties {

        @Test
        @DisplayName("默认值")
        void defaults() {
            FuHttpTaskWorkerProperties props = new FuHttpTaskWorkerProperties();

            assertNotNull(props.getRedis());
            assertTrue(props.getRedis().isEmpty());
            assertNull(props.getWorkers());
        }

        @Test
        @DisplayName("配置前缀为 retask4j.http")
        void configurationPrefix() {
            ConfigurationProperties annotation = FuHttpTaskWorkerProperties.class
                .getAnnotation(ConfigurationProperties.class);
            assertNotNull(annotation);
            assertEquals("retask4j.http", annotation.prefix());
        }
    }

    @Nested
    @DisplayName("Caller 与 Worker 共享 redis 配置")
    class SharedRedisConfig {

        @Test
        @DisplayName("两者使用相同的配置前缀")
        void samePrefix() {
            String callerPrefix = FuHttpTaskCallerProperties.class
                .getAnnotation(ConfigurationProperties.class)
                .prefix();
            String workerPrefix = FuHttpTaskWorkerProperties.class
                .getAnnotation(ConfigurationProperties.class)
                .prefix();

            assertEquals(callerPrefix, workerPrefix);
            assertEquals("retask4j.http", callerPrefix);
        }
    }
}
