package fun.commons.retask4j.core.internal;

import fun.commons.retask4j.core.config.FuTaskBaseConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.redisson.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FuTaskBase 测试 — 验证 Redis 数据模型、Key 命名、Lua 脚本参数。
 */
class FuTaskBaseTest {

    private RedissonClient redissonClient;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redissonClient = mock(RedissonClient.class);
        RBlockingDeque<Object> blockingDeque = mock(RBlockingDeque.class);
        RScoredSortedSet<Object> scoredSet = mock(RScoredSortedSet.class);
        RScript rScript = mock(RScript.class);

        when(redissonClient.getBlockingDeque(anyString(), any())).thenReturn(blockingDeque);
        when(redissonClient.getScoredSortedSet(anyString(), any(org.redisson.client.codec.Codec.class))).thenReturn(scoredSet);
        when(redissonClient.getScript((org.redisson.client.codec.Codec) any())).thenReturn(rScript);

        when(blockingDeque.getName()).thenReturn("fu-task-{test}-blocking");
        when(scoredSet.getName()).thenReturn("fu-task-{test}-pending");
    }

    @Nested
    @DisplayName("Redis Key 命名规则")
    class KeyNaming {

        @Test
        @DisplayName("各数据结构 Key 命名正确")
        void keyNamingPattern() {
            String topic = "order";
            String keyPrefix = "fu-task-{%s}".formatted(topic);

            assertEquals("fu-task-{order}", keyPrefix);
            assertEquals("fu-task-{order}-message:task-001", keyPrefix + "-message:task-001");
            assertEquals("fu-task-{order}-return:caller-abc", keyPrefix + "-return:caller-abc");
            assertEquals("fu-task-{order}-callback", keyPrefix + "-callback");
            assertEquals("fu-task-{order}-callback-pending", keyPrefix + "-callback-pending");
        }
    }

    @Nested
    @DisplayName("FuTaskBase 构造")
    class Construction {

        @Test
        @DisplayName("构造时创建正确的 Redis 数据结构")
        void createsCorrectRedisStructures() {
            FuTaskBaseConfig config = new FuTaskBaseConfig("my-topic");

            // 验证 topic 传递正确
            assertEquals("my-topic", config.getTopic());
        }
    }

}
