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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test for FUNCTION mode: caller submits task via funcAsync and
 * receives a CompletableFuture result when the worker completes.
 *
 * <p>Requires a running Redis. Run with:
 * <pre>mvn test -Dtest=EndToEndFunctionModeTest -Dredis.host=localhost</pre>
 */
class EndToEndFunctionModeTest extends EndToEndTestBase {

    private static final String TOPIC = "test-function-mode";
    private FuTaskCaller<JSONObject> caller;
    private FuTaskWorker worker;

    @BeforeEach
    void setUp() {
        FuTaskCallConfig<JSONObject> callConfig = new FuTaskCallConfig<>(TOPIC, JSONObject.class);
        callConfig.setExecuteExpire(60);
        callConfig.setResultExpire(60);
        callConfig.setRequestTimeout(30);
        caller = new FuTaskCaller<>(redisson, callConfig);
        caller.start();

        FuTaskWorkConfig workConfig = new FuTaskWorkConfig(TOPIC);
        workConfig.setMaxConsumeThreads(2);
        workConfig.setPendingTimeout(60);

        FuTaskExecutor<JSONObject, JSONObject> executor = new FuTaskExecutor<>(input -> {
            JSONObject output = new JSONObject();
            output.put("echo", input.getString("msg"));
            output.put("ts", System.currentTimeMillis());
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
    void funcAsyncReturnsFutureWithWorkerResult() throws Exception {
        JSONObject input = new JSONObject();
        input.put("msg", "hello-function");

        CompletableFuture<JSONObject> future = caller.funcAsync(caller.newTaskMessage(input));

        JSONObject result = future.get(10, TimeUnit.SECONDS);
        assertNotNull(result, "Future should resolve with a result");
        assertEquals("hello-function", result.getString("echo"));
        assertTrue(result.getLong("ts") > 0);
    }
}
