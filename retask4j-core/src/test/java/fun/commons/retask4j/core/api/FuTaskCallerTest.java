package fun.commons.retask4j.core.api;

import com.alibaba.fastjson2.JSONObject;
import fun.commons.retask4j.core.config.FuTaskCallConfig;
import fun.commons.retask4j.core.message.FuTaskMessage;
import fun.commons.retask4j.core.message.FuTaskMode;
import fun.commons.retask4j.core.message.FuTaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.redisson.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FuTaskCaller 测试 — 需要 mock RedissonClient。
 * 测试 Caller 的三种模式发送、Future 管理、回调逻辑。
 */
class FuTaskCallerTest {

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
            if (name.contains("-pending")) return pendingSet;
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
    @DisplayName("Caller 构造")
    class Construction {

        @Test
        @DisplayName("创建 Caller 生成 UUID 格式 callerId")
        void callerIdGenerated() throws Exception {
            FuTaskCallConfig<JSONObject> config = new FuTaskCallConfig<>("demo", JSONObject.class);
            FuTaskCaller<JSONObject> caller = new FuTaskCaller<>(redissonClient, config);

            FuTaskMessage msg = caller.newTaskMessage(new JSONObject());
            assertNotNull(msg.getCallerId());
            assertEquals(32, msg.getCallerId().length());
            assertTrue(msg.getCallerId().matches("[0-9a-f]+"));
        }

        @Test
        @DisplayName("不同 Caller 实例的 callerId 不同")
        void uniqueCallerIds() {
            FuTaskCallConfig<JSONObject> config = new FuTaskCallConfig<>("demo", JSONObject.class);
            FuTaskCaller<JSONObject> caller1 = new FuTaskCaller<>(redissonClient, config);
            FuTaskCaller<JSONObject> caller2 = new FuTaskCaller<>(redissonClient, config);

            FuTaskMessage msg1 = caller1.newTaskMessage(new JSONObject());
            FuTaskMessage msg2 = caller2.newTaskMessage(new JSONObject());

            assertNotEquals(msg1.getCallerId(), msg2.getCallerId());
        }

        @Test
        @DisplayName("带 Callback Consumer 的构造")
        void constructionWithCallback() {
            Consumer<FuTaskMessage> callback = msg -> {};
            FuTaskCallConfig<JSONObject> config = new FuTaskCallConfig<>("demo", JSONObject.class);
            assertDoesNotThrow(() -> new FuTaskCaller<>(redissonClient, config, callback));
        }
    }

    @Nested
    @DisplayName("newTaskMessage")
    class NewTaskMessage {

        @Test
        @DisplayName("自动生成 32 位随机 ID")
        void autoId() {
            FuTaskCallConfig<JSONObject> config = new FuTaskCallConfig<>("demo", JSONObject.class);
            FuTaskCaller<JSONObject> caller = new FuTaskCaller<>(redissonClient, config);

            FuTaskMessage msg = caller.newTaskMessage(new JSONObject().fluentPut("key", "val"));
            assertNotNull(msg.getId());
            assertEquals(32, msg.getId().length());
            assertEquals("demo", msg.getTopic());
            assertEquals(FuTaskMode.NORMAL, msg.getMode());
            assertNotNull(msg.getInput());
        }

        @Test
        @DisplayName("指定 ID")
        void specifiedId() {
            FuTaskCallConfig<JSONObject> config = new FuTaskCallConfig<>("demo", JSONObject.class);
            FuTaskCaller<JSONObject> caller = new FuTaskCaller<>(redissonClient, config);

            FuTaskMessage msg = caller.newTaskMessage("my-id", new JSONObject());
            assertEquals("my-id", msg.getId());
        }

        @Test
        @DisplayName("配置字段正确传播到消息")
        void configFieldsPropagated() {
            FuTaskCallConfig<JSONObject> config = new FuTaskCallConfig<>("demo", JSONObject.class);
            config.setRetryPlan(List.of(5, 20));
            config.setExecuteExpire(7200);
            config.setResultExpire(600);
            config.setStrategy("fast");

            FuTaskCaller<JSONObject> caller = new FuTaskCaller<>(redissonClient, config);
            FuTaskMessage msg = caller.newTaskMessage(new JSONObject());

            assertEquals(config.getRetryPlan(), msg.getRetryPlan());
            assertEquals(config.getExecuteExpire(), msg.getExecuteExpire());
            assertEquals(config.getResultExpire(), msg.getResultExpire());
            assertEquals(config.getStrategy(), msg.getStrategy());
        }
    }

    @Nested
    @DisplayName("NORMAL 模式发送")
    class NormalMode {

        @Test
        @DisplayName("sendTaskMessage 单条发送")
        void sendSingle() {
            FuTaskCallConfig<JSONObject> config = new FuTaskCallConfig<>("demo", JSONObject.class);
            FuTaskCaller<JSONObject> caller = new FuTaskCaller<>(redissonClient, config);

            FuTaskMessage msg = caller.newTaskMessage(new JSONObject());
            int result = caller.sendTaskMessage(msg);

            assertEquals(FuTaskMode.NORMAL, msg.getMode());
            assertTrue(result >= 0);
        }

        @Test
        @DisplayName("sendTaskMessage 批量发送")
        void sendBatch() {
            FuTaskCallConfig<JSONObject> config = new FuTaskCallConfig<>("demo", JSONObject.class);
            FuTaskCaller<JSONObject> caller = new FuTaskCaller<>(redissonClient, config);

            List<FuTaskMessage> messages = List.of(
                caller.newTaskMessage(new JSONObject()),
                caller.newTaskMessage(new JSONObject())
            );
            int result = caller.sendTaskMessage(messages);

            messages.forEach(m -> assertEquals(FuTaskMode.NORMAL, m.getMode()));
            assertTrue(result >= 0);
        }
    }

    @Nested
    @DisplayName("FUNCTION 模式发送")
    class FunctionMode {

        @Test
        @DisplayName("funcAsync 返回 CompletableFuture")
        void funcAsyncReturnsFuture() {
            FuTaskCallConfig<JSONObject> config = new FuTaskCallConfig<>("demo", JSONObject.class);
            FuTaskCaller<JSONObject> caller = new FuTaskCaller<>(redissonClient, config);

            FuTaskMessage msg = caller.newTaskMessage(new JSONObject());
            CompletableFuture<JSONObject> future = caller.funcAsync(msg);

            assertNotNull(future);
            assertEquals(FuTaskMode.FUNCTION, msg.getMode());
            // resultExpire 最小为 60
            assertTrue(msg.getResultExpire() >= 60);
        }

        @Test
        @DisplayName("funcAsync 带 BiConsumer 回调")
        void funcAsyncWithCallback() {
            FuTaskCallConfig<JSONObject> config = new FuTaskCallConfig<>("demo", JSONObject.class);
            FuTaskCaller<JSONObject> caller = new FuTaskCaller<>(redissonClient, config);

            FuTaskMessage msg = caller.newTaskMessage(new JSONObject());
            int count = caller.funcAsync(msg, (result, error) -> {});

            assertTrue(count >= 0);
        }

        @Test
        @DisplayName("funcAsync 带 CompletableFuture 参数")
        void funcAsyncWithExternalFuture() {
            FuTaskCallConfig<JSONObject> config = new FuTaskCallConfig<>("demo", JSONObject.class);
            FuTaskCaller<JSONObject> caller = new FuTaskCaller<>(redissonClient, config);

            FuTaskMessage msg = caller.newTaskMessage(new JSONObject());
            CompletableFuture<JSONObject> externalFuture = new CompletableFuture<>();
            caller.funcAsync(msg, externalFuture);

            assertFalse(externalFuture.isDone());
        }

        @Test
        @DisplayName("sendFuncMessage 设置 FUNCTION 模式和最小 resultExpire")
        void sendFuncMessageSetsModeAndExpire() {
            FuTaskCallConfig<JSONObject> config = new FuTaskCallConfig<>("demo", JSONObject.class);
            config.setResultExpire(10); // 小于 60
            FuTaskCaller<JSONObject> caller = new FuTaskCaller<>(redissonClient, config);

            FuTaskMessage msg = caller.newTaskMessage(new JSONObject());
            caller.sendFuncMessage(msg);

            assertEquals(FuTaskMode.FUNCTION, msg.getMode());
            assertTrue(msg.getResultExpire() >= 60);
        }
    }

    @Nested
    @DisplayName("CALLBACK 模式发送")
    class CallbackMode {

        @Test
        @DisplayName("sendCallbackMessage 设置 CALLBACK 模式")
        void sendCallbackSetsMode() {
            FuTaskCallConfig<JSONObject> config = new FuTaskCallConfig<>("demo", JSONObject.class);
            FuTaskCaller<JSONObject> caller = new FuTaskCaller<>(redissonClient, config);

            FuTaskMessage msg = caller.newTaskMessage(new JSONObject());
            caller.sendCallbackMessage(msg);

            assertEquals(FuTaskMode.CALLBACK, msg.getMode());
            assertTrue(msg.getResultExpire() >= 60);
        }

        @Test
        @DisplayName("sendCallbackMessage 批量")
        void sendCallbackBatch() {
            FuTaskCallConfig<JSONObject> config = new FuTaskCallConfig<>("demo", JSONObject.class);
            FuTaskCaller<JSONObject> caller = new FuTaskCaller<>(redissonClient, config);

            List<FuTaskMessage> messages = List.of(
                caller.newTaskMessage(new JSONObject()),
                caller.newTaskMessage(new JSONObject())
            );
            caller.sendCallbackMessage(messages);

            messages.forEach(m -> assertEquals(FuTaskMode.CALLBACK, m.getMode()));
        }
    }

    @Nested
    @DisplayName("completeFuncFuture")
    class CompleteFuncFuture {

        @Test
        @DisplayName("成功完成时 Future 正确解析")
        void successComplete() {
            FuTaskCallConfig<JSONObject> config = new FuTaskCallConfig<>("demo", JSONObject.class);
            FuTaskCaller<JSONObject> caller = new FuTaskCaller<>(redissonClient, config);

            FuTaskMessage msg = caller.newTaskMessage(new JSONObject());
            CompletableFuture<JSONObject> future = caller.funcAsync(msg);

            // 模拟 Worker 完成任务
            FuTaskMessage completedMsg = new FuTaskMessage("demo", msg.getId());
            completedMsg.setStatus(FuTaskStatus.SUCCESS);
            completedMsg.setOutput(new JSONObject().fluentPut("result", "done"));

            caller.completeFuncFuture(List.of(completedMsg));

            assertTrue(future.isDone());
            assertFalse(future.isCompletedExceptionally());
        }

        @Test
        @DisplayName("失败完成时 Future 异常完成")
        void failComplete() {
            FuTaskCallConfig<JSONObject> config = new FuTaskCallConfig<>("demo", JSONObject.class);
            FuTaskCaller<JSONObject> caller = new FuTaskCaller<>(redissonClient, config);

            FuTaskMessage msg = caller.newTaskMessage(new JSONObject());
            CompletableFuture<JSONObject> future = caller.funcAsync(msg);

            FuTaskMessage failedMsg = new FuTaskMessage("demo", msg.getId());
            failedMsg.setStatus(FuTaskStatus.FAIL);
            failedMsg.setError("execution failed");

            caller.completeFuncFuture(List.of(failedMsg));

            assertTrue(future.isDone());
            assertTrue(future.isCompletedExceptionally());
        }

        @Test
        @DisplayName("非 SUCCESS 状态的 Future 异常完成")
        void nonSuccessStatusComplete() {
            FuTaskCallConfig<JSONObject> config = new FuTaskCallConfig<>("demo", JSONObject.class);
            FuTaskCaller<JSONObject> caller = new FuTaskCaller<>(redissonClient, config);

            FuTaskMessage msg = caller.newTaskMessage(new JSONObject());
            CompletableFuture<JSONObject> future = caller.funcAsync(msg);

            FuTaskMessage failMsg = new FuTaskMessage("demo", msg.getId());
            failMsg.setStatus(FuTaskStatus.FAIL);

            caller.completeFuncFuture(List.of(failMsg));

            assertTrue(future.isDone());
            assertTrue(future.isCompletedExceptionally());
        }

        @Test
        @DisplayName("不存在的 ID 不影响已有 Future")
        void unknownIdNoEffect() {
            FuTaskCallConfig<JSONObject> config = new FuTaskCallConfig<>("demo", JSONObject.class);
            FuTaskCaller<JSONObject> caller = new FuTaskCaller<>(redissonClient, config);

            FuTaskMessage msg = caller.newTaskMessage(new JSONObject());
            CompletableFuture<JSONObject> future = caller.funcAsync(msg);

            // 用不相关的 ID 完成
            FuTaskMessage otherMsg = new FuTaskMessage("demo", "non-existent-id");
            otherMsg.setStatus(FuTaskStatus.SUCCESS);
            otherMsg.setOutput(new JSONObject());

            caller.completeFuncFuture(List.of(otherMsg));

            // 原 Future 不受影响
            assertFalse(future.isDone());
        }
    }

    @Nested
    @DisplayName("批量发送 BatchManager")
    class BatchSend {

        @Test
        @DisplayName("sendTaskMessageBatch 通过 BatchManager 发送")
        void batchSend() {
            FuTaskCallConfig<JSONObject> config = new FuTaskCallConfig<>("demo", JSONObject.class);
            FuTaskCaller<JSONObject> caller = new FuTaskCaller<>(redissonClient, config);

            FuTaskMessage msg = caller.newTaskMessage(new JSONObject());
            // sendTaskMessageBatch 使用 BatchManager，异步攒批
            int result = caller.sendTaskMessageBatch(msg);
            // 返回值可能为 0（攒批中）或 1（已刷新）
            assertTrue(result >= 0);
        }

        @Test
        @DisplayName("sendFuncMessageBatch 通过 BatchManager 发送")
        void funcBatchSend() {
            FuTaskCallConfig<JSONObject> config = new FuTaskCallConfig<>("demo", JSONObject.class);
            FuTaskCaller<JSONObject> caller = new FuTaskCaller<>(redissonClient, config);

            FuTaskMessage msg = caller.newTaskMessage(new JSONObject());
            CompletableFuture<JSONObject> future = caller.funcAsyncBatch(msg);

            assertNotNull(future);
            assertEquals(FuTaskMode.FUNCTION, msg.getMode());
        }

        @Test
        @DisplayName("sendCallbackMessageBatch 通过 BatchManager 发送")
        void callbackBatchSend() {
            FuTaskCallConfig<JSONObject> config = new FuTaskCallConfig<>("demo", JSONObject.class);
            FuTaskCaller<JSONObject> caller = new FuTaskCaller<>(redissonClient, config);

            FuTaskMessage msg = caller.newTaskMessage(new JSONObject());
            caller.sendCallbackMessageBatch(msg);

            assertEquals(FuTaskMode.CALLBACK, msg.getMode());
        }
    }
}
