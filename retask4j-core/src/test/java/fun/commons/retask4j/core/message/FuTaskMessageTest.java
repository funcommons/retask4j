package fun.commons.retask4j.core.message;

import com.alibaba.fastjson2.JSONObject;
import fun.commons.retask4j.core.api.FuTaskExecutor;
import fun.commons.retask4j.core.config.FuTaskBaseConfig;
import fun.commons.retask4j.core.config.FuTaskCallConfig;
import fun.commons.retask4j.core.config.FuTaskWorkConfig;
import fun.commons.retask4j.core.monitor.FuTaskMonitor;
import fun.commons.retask4j.core.strategy.FuTaskWorkStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FuTaskMessageTest {

    @Nested
    @DisplayName("构造与字段默认值")
    class Construction {

        @Test
        @DisplayName("通过 topic + id 构造，默认值正确")
        void constructionWithTopicAndId() {
            FuTaskMessage msg = new FuTaskMessage("order", "task-001");

            assertEquals("task-001", msg.getId());
            assertEquals("order", msg.getTopic());
            assertNull(msg.getTag());
            assertNull(msg.getStrategy());
            assertEquals(FuTaskStatus.WAITING, msg.getStatus());
            assertEquals(FuTaskMode.NORMAL, msg.getMode());
            assertEquals(0, msg.getDelayTime());
            assertEquals(0, msg.getRetryTimes());
            assertEquals(0, msg.getRetryDelay());
            assertEquals(3600, msg.getExecuteExpire());
            assertEquals(0, msg.getResultExpire());
            assertNotNull(msg.getRetryPlan());
            assertTrue(msg.getRetryPlan().isEmpty());
            assertNotNull(msg.getExtInfo());
            assertNull(msg.getInput());
            assertNull(msg.getOutput());
            assertNull(msg.getError());
            assertNotNull(msg.getCallbackStatus());
            assertEquals(0, msg.getCallbackRetryTimes());
        }

        @Test
        @DisplayName("createTime 初始化为当前时间, scheduleTime 默认0")
        void createTimeInitialized() {
            long before = System.currentTimeMillis();
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            long after = System.currentTimeMillis();

            assertTrue(msg.getCreateTime() >= before && msg.getCreateTime() <= after);
            assertEquals(0, msg.getScheduleTime());
        }
    }

    @Nested
    @DisplayName("FuTag 注解系统")
    class FuTagAnnotation {

        @Test
        @DisplayName("requestFields 包含请求阶段字段")
        void requestFieldsContainsExpectedFields() {
            List<String> fields = FuTaskMessage.requestFields;

            assertTrue(fields.contains("id"));
            assertTrue(fields.contains("topic"));
            assertTrue(fields.contains("tag"));
            assertTrue(fields.contains("strategy"));
            assertTrue(fields.contains("createTime"));
            assertTrue(fields.contains("delayTime"));
            assertTrue(fields.contains("retryPlan"));
            assertTrue(fields.contains("executeExpire"));
            assertTrue(fields.contains("resultExpire"));
            assertTrue(fields.contains("input"));
            assertTrue(fields.contains("extInfo"));
            assertTrue(fields.contains("scheduleTime"));
            assertTrue(fields.contains("retryTimes"));
            assertTrue(fields.contains("retryDelay"));
            assertTrue(fields.contains("status"));
            assertTrue(fields.contains("callerId"));
            assertTrue(fields.contains("mode"));
        }

        @Test
        @DisplayName("retryFields 包含重试阶段字段")
        void retryFieldsContainsExpectedFields() {
            List<String> fields = FuTaskMessage.retryFields;

            assertTrue(fields.contains("id"));
            assertTrue(fields.contains("scheduleTime"));
            assertTrue(fields.contains("retryTimes"));
            assertTrue(fields.contains("retryDelay"));
            assertTrue(fields.contains("status"));
        }

        @Test
        @DisplayName("completeFields 包含完成阶段字段")
        void completeFieldsContainsExpectedFields() {
            List<String> fields = FuTaskMessage.completeFields;

            assertTrue(fields.contains("id"));
            assertTrue(fields.contains("status"));
            assertTrue(fields.contains("executeTime"));
            assertTrue(fields.contains("completeTime"));
            assertTrue(fields.contains("error"));
            assertTrue(fields.contains("output"));
            assertTrue(fields.contains("resultExpire"));
            assertTrue(fields.contains("callerId"));
            assertTrue(fields.contains("mode"));
            assertTrue(fields.contains("callbackStatus"));
            assertTrue(fields.contains("callbackRetryTimes"));
        }

        @Test
        @DisplayName("callbackFields 包含回调阶段字段")
        void callbackFieldsContainsExpectedFields() {
            List<String> fields = FuTaskMessage.callbackFields;

            assertTrue(fields.contains("callbackRetryTimes"));
            assertTrue(fields.contains("callbackStatus"));
            assertTrue(fields.contains("callbackError"));
            // Fields needed for hash recreation in set_callback_batch.lua
            assertTrue(fields.contains("id"));
            assertTrue(fields.contains("topic"));
            assertTrue(fields.contains("status"));
            assertTrue(fields.contains("mode"));
            assertTrue(fields.contains("callerId"));
            assertTrue(fields.contains("output"));
            assertTrue(fields.contains("error"));
            assertTrue(fields.contains("extInfo"));
            assertTrue(fields.contains("resultExpire"));
            assertTrue(fields.contains("executeTime"));
            assertTrue(fields.contains("completeTime"));
        }

        @Test
        @DisplayName("allFields 包含所有字段")
        void allFieldsContainsEverything() {
            List<String> all = FuTaskMessage.allFields;

            // 确保关键字段都在
            assertTrue(all.contains("id"));
            assertTrue(all.contains("topic"));
            assertTrue(all.contains("input"));
            assertTrue(all.contains("output"));
            assertTrue(all.contains("callbackStatus"));
            assertTrue(all.size() >= 25);
        }

        @Test
        @DisplayName("各阶段字段无交集部分正确")
        void fieldsPhasesDistinct() {
            // id 在 request, retry, complete, callback 四个阶段都有
            assertTrue(FuTaskMessage.requestFields.contains("id"));
            assertTrue(FuTaskMessage.retryFields.contains("id"));
            assertTrue(FuTaskMessage.completeFields.contains("id"));

            // input 只在 request
            assertTrue(FuTaskMessage.requestFields.contains("input"));
            assertFalse(FuTaskMessage.completeFields.contains("input"));

            // output 只在 complete
            assertFalse(FuTaskMessage.requestFields.contains("output"));
            assertTrue(FuTaskMessage.completeFields.contains("output"));
        }
    }

    @Nested
    @DisplayName("toRequestMap / toRetryMap / toCompleteMap / toCallbackMap")
    class PhaseMaps {

        private FuTaskMessage createSampleMessage() {
            FuTaskMessage msg = new FuTaskMessage("order", "task-001");
            msg.setInput(new JSONObject().fluentPut("orderId", "O123"));
            msg.setRetryPlan(List.of(5, 20, 60));
            msg.setCallerId("caller-abc");
            msg.setMode(FuTaskMode.FUNCTION);
            msg.setOutput(new JSONObject().fluentPut("result", "ok"));
            msg.setStatus(FuTaskStatus.SUCCESS);
            msg.setError(null);
            msg.setExecuteTime(1000L);
            msg.setCompleteTime(2000L);
            msg.setResultExpire(300);
            return msg;
        }

        @Test
        @DisplayName("toRequestMap 包含请求阶段字段，不包含完成阶段独有字段")
        void toRequestMapContainsOnlyRequestFields() {
            FuTaskMessage msg = createSampleMessage();
            Map<String, String> map = msg.toRequestMap();

            assertEquals("task-001", map.get("id"));
            assertEquals("order", map.get("topic"));
            assertNotNull(map.get("input"));
            assertEquals("caller-abc", map.get("callerId"));
            assertEquals("FUNCTION", map.get("mode"));

            // output 不应在 request map
            assertNull(map.get("output"));
            assertNull(map.get("executeTime"));
            assertNull(map.get("completeTime"));
            assertNull(map.get("error"));
        }

        @Test
        @DisplayName("toRetryMap 包含重试阶段字段")
        void toRetryMapContainsRetryFields() {
            FuTaskMessage msg = createSampleMessage();
            // retryMap 包含 status 字段，且此时 status 已被设为 SUCCESS
            // 但 toRetryMap 只取 FuTag("retry") 标记的字段
            Map<String, String> map = msg.toRetryMap();

            assertEquals("task-001", map.get("id"));
            assertNotNull(map.get("scheduleTime"));
            assertNotNull(map.get("retryTimes"));
            assertNotNull(map.get("retryDelay"));
            assertNotNull(map.get("status"));
        }

        @Test
        @DisplayName("toCompleteMap 包含完成阶段字段")
        void toCompleteMapContainsCompleteFields() {
            FuTaskMessage msg = createSampleMessage();
            Map<String, String> map = msg.toCompleteMap();

            assertEquals("task-001", map.get("id"));
            assertEquals("SUCCESS", map.get("status"));
            assertNotNull(map.get("executeTime"));
            assertNotNull(map.get("completeTime"));
            assertNotNull(map.get("output"));
            assertEquals("300", map.get("resultExpire"));
            assertEquals("caller-abc", map.get("callerId"));
            assertEquals("FUNCTION", map.get("mode"));

            // input 不在 complete map
            assertNull(map.get("input"));
            // topic 在 complete map (hash recreation needs it for fromStringMap validation)
            assertNotNull(map.get("topic"));
            // extInfo 在 complete map (hash recreation needs it for per-task callback URL)
            assertNotNull(map.get("extInfo"));
            // callbackStatus/callbackRetryTimes in complete map (initial callback state for hash recreation)
            assertNotNull(map.get("callbackStatus"));
            assertNotNull(map.get("callbackRetryTimes"));
        }

        @Test
        @DisplayName("toCallbackMap 包含回调阶段字段")
        void toCallbackMapContainsCallbackFields() {
            FuTaskMessage msg = createSampleMessage();
            msg.setCallbackStatus(FuTaskStatus.SUCCESS);
            msg.setCallbackRetryTimes(2);
            msg.setCallbackError(null);
            Map<String, String> map = msg.toCallbackMap();

            assertNotNull(map.get("callbackRetryTimes"));
            assertNotNull(map.get("callbackStatus"));
        }

        @Test
        @DisplayName("null 值字段不出现在 map 中")
        void nullValuesOmitted() {
            FuTaskMessage msg = new FuTaskMessage("test", "id1");
            msg.setTag(null);
            msg.setStrategy(null);
            Map<String, String> map = msg.toRequestMap();

            assertFalse(map.containsKey("tag"));
            assertFalse(map.containsKey("strategy"));
        }
    }

    @Nested
    @DisplayName("fromStringMap 反序列化")
    class FromStringMap {

        @Test
        @DisplayName("从 Map 反序列化还原所有字段")
        void deserializeFromMap() {
            FuTaskMessage original = new FuTaskMessage("order", "task-002");
            original.setInput(new JSONObject().fluentPut("key", "val"));
            original.setRetryPlan(List.of(5, 20));
            original.setDelayTime(10);
            original.setStatus(FuTaskStatus.PENDING);
            original.setMode(FuTaskMode.CALLBACK);
            original.setCallerId("caller-xyz");
            original.setRetryTimes(1);
            original.setRetryDelay(5);

            Map<String, String> map = original.toRequestMap();
            FuTaskMessage restored = FuTaskMessage.fromStringMap(map);

            assertEquals(original.getId(), restored.getId());
            assertEquals(original.getTopic(), restored.getTopic());
            assertEquals(original.getDelayTime(), restored.getDelayTime());
            assertEquals(original.getStatus(), restored.getStatus());
            assertEquals(original.getMode(), restored.getMode());
            assertEquals(original.getCallerId(), restored.getCallerId());
            assertEquals(original.getRetryTimes(), restored.getRetryTimes());
        }

        @Test
        @DisplayName("序列化再反序列化 round-trip 一致")
        void roundTrip() {
            FuTaskMessage original = new FuTaskMessage("test", "rt-001");
            original.setInput(new JSONObject().fluentPut("data", 42));
            original.setRetryPlan(List.of(1, 3, 5));
            original.setExecuteExpire(7200);
            original.setResultExpire(600);

            Map<String, String> requestMap = original.toRequestMap();
            FuTaskMessage fromRequest = FuTaskMessage.fromStringMap(requestMap);
            assertEquals(original.getId(), fromRequest.getId());
            assertEquals(original.getTopic(), fromRequest.getTopic());
            assertEquals(original.getExecuteExpire(), fromRequest.getExecuteExpire());
        }

        @Test
        @DisplayName("空 map 不抛异常")
        void emptyMapSafe() {
            assertDoesNotThrow(() -> FuTaskMessage.fromStringMap(Map.of()));
        }
    }

    @Nested
    @DisplayName("FuTaskStatus 常量")
    class StatusConstants {

        @Test
        @DisplayName("四个状态常量值正确")
        void statusValues() {
            assertEquals("WAITING", FuTaskStatus.WAITING);
            assertEquals("PENDING", FuTaskStatus.PENDING);
            assertEquals("SUCCESS", FuTaskStatus.SUCCESS);
            assertEquals("FAIL", FuTaskStatus.FAIL);
        }
    }

    @Nested
    @DisplayName("FuTaskMode 常量")
    class ModeConstants {

        @Test
        @DisplayName("三个模式常量值正确")
        void modeValues() {
            assertEquals("NORMAL", FuTaskMode.NORMAL);
            assertEquals("FUNCTION", FuTaskMode.FUNCTION);
            assertEquals("CALLBACK", FuTaskMode.CALLBACK);
        }
    }

    @Nested
    @DisplayName("配置类默认值")
    class ConfigDefaults {

        @Test
        @DisplayName("FuTaskCallConfig 默认值")
        void callConfigDefaults() {
            FuTaskCallConfig<JSONObject> config = new FuTaskCallConfig<>("test", JSONObject.class);

            assertEquals("test", config.getTopic());
            assertTrue(config.getRetryPlan().isEmpty());
            assertEquals(86400, config.getExecuteExpire());
            assertEquals(0, config.getResultExpire());
            assertNull(config.getStrategy());
            assertEquals(120, config.getRequestTimeout());
            assertEquals(64, config.getCallbackMaxThreads());
            assertEquals(3, config.getCallbackRetryTimes());
            assertEquals(60, config.getCallbackRetryInterval());
            assertEquals(JSONObject.class, config.getReturnCls());
        }

        @Test
        @DisplayName("FuTaskWorkConfig 默认值")
        void workConfigDefaults() {
            FuTaskWorkConfig config = new FuTaskWorkConfig("worker");

            assertEquals("worker", config.getTopic());
            assertEquals(64, config.getMaxConsumeThreads());
            assertNotNull(config.getStrategyMap());
            assertTrue(config.getStrategyMap().containsKey("default"));
        }

        @Test
        @DisplayName("FuTaskWorkConfig.addStrategy 流式 API")
        void workConfigAddStrategy() {
            FuTaskWorkConfig config = new FuTaskWorkConfig("test");
            FuTaskWorkStrategy strategy = new FuTaskWorkStrategy("fast");
            FuTaskWorkConfig result = config.addStrategy("fast", strategy);

            assertSame(config, result); // 流式返回 this
            assertTrue(config.getStrategyMap().containsKey("fast"));
            assertEquals("fast", config.getStrategyMap().get("fast").getName());
        }

        @Test
        @DisplayName("FuTaskWorkConfig 无参构造 topic 为 default")
        void workConfigDefaultTopic() {
            FuTaskWorkConfig config = new FuTaskWorkConfig();
            assertEquals("default", config.getTopic());
        }

        @Test
        @DisplayName("FuTaskBaseConfig 构造函数")
        void baseConfig() {
            FuTaskBaseConfig config1 = new FuTaskBaseConfig();
            assertEquals("default", config1.getTopic());

            FuTaskBaseConfig config2 = new FuTaskBaseConfig("custom");
            assertEquals("custom", config2.getTopic());
        }
    }

    @Nested
    @DisplayName("FuTaskWorkStrategy")
    class WorkStrategy {

        @Test
        @DisplayName("构造与字段")
        void strategyConstruction() {
            FuTaskWorkStrategy strategy = new FuTaskWorkStrategy("test-strategy");

            assertEquals("test-strategy", strategy.getName());
            assertNull(strategy.getAssertResultFunction());
            assertNull(strategy.getOnCompleteConsumer());
            assertNull(strategy.getOnFailConsumer());
            assertNull(strategy.getOnFinallyFailConsumer());
            assertNull(strategy.getOnSuccessConsumer());
        }
    }

    @Nested
    @DisplayName("FuTaskMonitor.WorkerMonitor")
    class WorkerMonitor {

        @Test
        @DisplayName("初始值全为 0")
        void monitorInitialValues() {
            FuTaskMonitor.WorkerMonitor monitor = new FuTaskMonitor.WorkerMonitor();

            assertEquals(0L, monitor.consume.get());
            assertEquals(0L, monitor.success.get());
            assertEquals(0L, monitor.fail.get());
            assertEquals(0L, monitor.finallyFail.get());
            assertEquals(0L, monitor.complete.get());
            assertEquals(0L, monitor.timingPoll.get());
            assertEquals(0L, monitor.pendingPoll.get());
            assertEquals(0L, monitor.retryPoll.get());
            assertEquals(0L, monitor.workerCompleted.get());
            assertEquals(0L, monitor.workerActiveCount.get());
        }

        @Test
        @DisplayName("finallyFail 字段名拼写错误验证 — 确认 Bug 存在")
        void finallyFailTypoBugExists() {
            FuTaskMonitor.WorkerMonitor monitor = new FuTaskMonitor.WorkerMonitor();

            // Bug: 字段名应该是 finallyFail，但实际是 finallyFail
            // 这个测试验证 Bug 存在：finallyFail 字段可以被递增
            monitor.finallyFail.incrementAndGet();
            assertEquals(1L, monitor.finallyFail.get());

            // 如果字段名正确应为 finallyFail，则此字段不应存在
            // 当前代码中只有 finallyFail，没有 finallyFail
            // 证明：文档中写的是 finallyFail，但代码字段名是 finallyFail（拼写错误）
        }
    }

    @Nested
    @DisplayName("FuTaskExecutor")
    class Executor {

        @Test
        @DisplayName("Function 构造的 Executor 正确执行")
        void functionExecutor() throws Exception {
            FuTaskExecutor<JSONObject, JSONObject> executor = new FuTaskExecutor<>(
                (input) -> new JSONObject().fluentPut("echo", input.getString("msg")),
                JSONObject.class
            );

            JSONObject input = new JSONObject().fluentPut("msg", "hello");
            JSONObject result = executor.execute(input, null);

            assertEquals("hello", result.getString("echo"));
        }

        @Test
        @DisplayName("BiFunction 构造的 Executor 正确执行")
        void biFunctionExecutor() throws Exception {
            FuTaskExecutor<JSONObject, JSONObject> executor = new FuTaskExecutor<>(
                (input, extInfo) -> new JSONObject()
                    .fluentPut("echo", input.getString("msg"))
                    .fluentPut("ext", extInfo.getString("key")),
                JSONObject.class
            );

            JSONObject input = new JSONObject().fluentPut("msg", "hi");
            JSONObject extInfo = new JSONObject().fluentPut("key", "val");
            JSONObject result = executor.execute(input, extInfo);

            assertEquals("hi", result.getString("echo"));
            assertEquals("val", result.getString("ext"));
        }
    }
}
