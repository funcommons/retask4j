package fun.commons.retask4j.core.integration;

import com.alibaba.fastjson2.JSONObject;
import fun.commons.retask4j.core.api.FuTaskCaller;
import fun.commons.retask4j.core.api.FuTaskExecutor;
import fun.commons.retask4j.core.api.FuTaskWorker;
import fun.commons.retask4j.core.config.FuTaskCallConfig;
import fun.commons.retask4j.core.config.FuTaskWorkConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test for NORMAL mode: caller sends a task, worker consumes and executes it.
 *
 * <p>Requires a running Redis. Run with:
 * <pre>mvn test -Dtest=EndToEndNormalModeTest -Dredis.host=localhost</pre>
 */
class EndToEndNormalModeTest extends EndToEndTestBase {

    private static final String TOPIC = "test-normal-mode";
    private FuTaskCaller<JSONObject> caller;
    private FuTaskWorker worker;
    private final java.util.concurrent.atomic.AtomicReference<JSONObject> received = new java.util.concurrent.atomic.AtomicReference<>();
    private final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);

    @BeforeEach
    void setUp() {
        FuTaskCallConfig<JSONObject> callConfig = new FuTaskCallConfig<>(TOPIC, JSONObject.class);
        callConfig.setExecuteExpire(60);
        callConfig.setResultExpire(60);
        caller = new FuTaskCaller<>(redisson, callConfig);
        caller.start();

        FuTaskWorkConfig workConfig = new FuTaskWorkConfig(TOPIC);
        workConfig.setMaxConsumeThreads(2);
        workConfig.setPendingTimeout(60);

        FuTaskExecutor<JSONObject, JSONObject> executor = new FuTaskExecutor<>(input -> {
            received.set(input);
            done.countDown();
            return input;
        }, JSONObject.class);

        worker = new FuTaskWorker(redisson, workConfig, executor);
        worker.start();
    }

    @AfterEach
    void tearDown() {
        if (caller != null) caller.shutdown();
        if (worker != null) worker.shutdown();
    }

    @Test
    void callerSendsTask_workerExecutes() throws Exception {
        JSONObject input = new JSONObject();
        input.put("orderId", "ORD-001");
        input.put("amount", 100);

        caller.sendTaskMessage(caller.newTaskMessage(input));

        assertTrue(done.await(10, TimeUnit.SECONDS), "Worker should have executed the task within 10s");
        assertNotNull(received.get(), "Worker should have received the task input");
        assertEquals("ORD-001", received.get().getString("orderId"));
        assertEquals(Integer.valueOf(100), received.get().getInteger("amount"));
    }
}
