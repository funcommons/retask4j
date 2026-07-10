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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test for retry behavior: task fails on first attempts, eventually succeeds.
 *
 * <p>Requires a running Redis. Run with:
 * <pre>mvn test -Dtest=EndToEndRetryTest -Dredis.host=localhost</pre>
 */
class EndToEndRetryTest extends EndToEndTestBase {

    private static final String TOPIC = "test-retry-mode";
    private FuTaskCaller<JSONObject> caller;
    private FuTaskWorker worker;
    private final AtomicInteger attempts = new AtomicInteger(0);
    private CountDownLatch done;

    @BeforeEach
    void setUp() {
        attempts.set(0);
        done = new CountDownLatch(1);

        FuTaskCallConfig<JSONObject> callConfig = new FuTaskCallConfig<>(TOPIC, JSONObject.class);
        callConfig.setExecuteExpire(60);
        callConfig.setResultExpire(60);
        // Retry plan: 1s, 1s, 1s (small delays so the test runs in seconds)
        callConfig.setRetryPlan(List.of(1, 1, 1));
        caller = new FuTaskCaller<>(redisson, callConfig);
        caller.start();

        FuTaskWorkConfig workConfig = new FuTaskWorkConfig(TOPIC);
        workConfig.setMaxConsumeThreads(2);
        workConfig.setPendingTimeout(60);

        FuTaskExecutor<JSONObject, JSONObject> executor = new FuTaskExecutor<>(input -> {
            int n = attempts.incrementAndGet();
            if (n < 3) {
                // Fail on first 2 attempts
                throw new RuntimeException("simulated failure #" + n);
            }
            // Succeed on the 3rd attempt
            JSONObject output = new JSONObject();
            output.put("attempts", n);
            done.countDown();
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
    void failingTaskRetriesUntilSuccess() throws Exception {
        JSONObject input = new JSONObject();
        input.put("id", "retry-001");

        caller.sendTaskMessage(caller.newTaskMessage(input));

        assertTrue(done.await(15, TimeUnit.SECONDS), "Task should eventually succeed after retries");
        assertEquals(3, attempts.get(), "Executor should have been invoked exactly 3 times");
    }
}
