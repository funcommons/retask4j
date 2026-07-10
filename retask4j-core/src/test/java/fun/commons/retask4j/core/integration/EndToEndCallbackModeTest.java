package fun.commons.retask4j.core.integration;

import com.alibaba.fastjson2.JSONObject;
import fun.commons.retask4j.core.api.FuTaskCaller;
import fun.commons.retask4j.core.api.FuTaskExecutor;
import fun.commons.retask4j.core.api.FuTaskWorker;
import fun.commons.retask4j.core.config.FuTaskCallConfig;
import fun.commons.retask4j.core.config.FuTaskWorkConfig;
import fun.commons.retask4j.core.message.FuTaskMessage;
import fun.commons.retask4j.core.message.FuTaskStatus;
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
 * End-to-end test for CALLBACK mode: caller submits a task; the constructor-level
 * callback consumer is invoked when the worker completes.
 *
 * <p>Requires a running Redis. Run with:
 * <pre>mvn test -Dtest=EndToEndCallbackModeTest -Dredis.host=localhost</pre>
 */
class EndToEndCallbackModeTest extends EndToEndTestBase {

    private static final String TOPIC = "test-callback-mode";
    private FuTaskCaller<JSONObject> caller;
    private FuTaskWorker worker;
    private AtomicReference<FuTaskMessage> callbackMsg;
    private CountDownLatch done;

    @BeforeEach
    void setUp() {
        callbackMsg = new AtomicReference<>();
        done = new CountDownLatch(1);

        FuTaskCallConfig<JSONObject> callConfig = new FuTaskCallConfig<>(TOPIC, JSONObject.class);
        callConfig.setExecuteExpire(60);
        callConfig.setResultExpire(60);
        callConfig.setCallbackMaxThreads(2);
        callConfig.setCallbackRetryInterval(1);
        callConfig.setCallbackPendingTimeout(10);
        caller = new FuTaskCaller<>(redisson, callConfig, completed -> {
            callbackMsg.set(completed);
            done.countDown();
        });
        caller.start();

        FuTaskWorkConfig workConfig = new FuTaskWorkConfig(TOPIC);
        workConfig.setMaxConsumeThreads(2);
        workConfig.setPendingTimeout(60);

        FuTaskExecutor<JSONObject, JSONObject> executor = new FuTaskExecutor<>(input -> {
            JSONObject output = new JSONObject();
            output.put("processed", true);
            return output;
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
    void workerCompletionTriggersCallback() throws Exception {
        JSONObject input = new JSONObject();
        input.put("id", "cb-001");

        caller.sendCallbackMessage(caller.newTaskMessage(input));

        assertTrue(done.await(10, TimeUnit.SECONDS), "Callback should have fired within 10s");
        assertNotNull(callbackMsg.get(), "Callback should have received the completed message");
        assertEquals("cb-001", callbackMsg.get().getInput().getString("id"));
        assertEquals(FuTaskStatus.SUCCESS, callbackMsg.get().getStatus());
    }
}
