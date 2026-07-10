package fun.commons.retask4j.core.api;

import com.alibaba.fastjson2.JSONObject;
import fun.commons.retask4j.core.config.FuTaskBaseConfig;
import fun.commons.retask4j.core.internal.FuTaskBase;
import fun.commons.retask4j.core.message.FuTaskMessage;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.redisson.api.RScript;
import org.redisson.api.RTopic;
import org.redisson.client.codec.Codec;
import org.redisson.codec.JsonJacksonCodec;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Admin/operations service for retask4j tasks. Provides peek, replay, force-retry,
 * force-complete, and delete operations. Thread-safe; one instance per (topic) is recommended.
 *
 * <p>Intended for use by operator tooling (dashboards, scripts, etc.) — not for the
 * normal task lifecycle (which is handled by FuTaskCaller and FuTaskWorker).
 */
@Slf4j
public class FuTaskAdminService {

    private final RedissonClient redisson;
    private final FuTaskBaseConfig config;
    private final Codec codec = new JsonJacksonCodec();

    private static final String DELETE_SCRIPT = loadScript("scripts/delete_task.lua");
    private static final String REPLAY_SCRIPT = loadScript("scripts/replay_task.lua");
    private static final String FORCE_RETRY_SCRIPT = loadScript("scripts/force_retry_task.lua");
    private static final String FORCE_COMPLETE_SCRIPT = loadScript("scripts/force_complete_task.lua");

    private final AtomicReference<String> deleteSha = new AtomicReference<>();
    private final AtomicReference<String> replaySha = new AtomicReference<>();
    private final AtomicReference<String> forceRetrySha = new AtomicReference<>();
    private final AtomicReference<String> forceCompleteSha = new AtomicReference<>();

    public FuTaskAdminService(RedissonClient redisson, FuTaskBaseConfig config) {
        this.redisson = redisson;
        this.config = config;
    }

    /**
     * Fetch a task message by ID. Returns null if the message hash does not exist.
     */
    public FuTaskMessage peek(String taskId) {
        ReTaskInfo info = new ReTaskInfo(redisson, config.getTopic());
        return info.getMessageById(taskId);
    }

    /**
     * Replay a task: reset retry counter, set status to WAITING, push to work deque.
     * Returns true on success, false if the message hash does not exist.
     */
    public boolean replay(String taskId) {
        if (peek(taskId) == null) return false;
        String prefix = "fu-task-" + config.getTopic() + "-";
        Object result = evalScript(RScript.Mode.READ_WRITE, REPLAY_SCRIPT, replaySha,
                RScript.ReturnType.INTEGER,
                List.of(
                        prefix + "message:" + taskId,
                        prefix + "blocking"
                ),
                taskId, String.valueOf(System.currentTimeMillis()));
        return result instanceof Long && (Long) result == 1L;
    }

    /**
     * Force a task to retry immediately (skip retryDelay). Removes it from pending set
     * and pushes it back to the work deque.
     */
    public boolean forceRetry(String taskId) {
        if (peek(taskId) == null) return false;
        String prefix = "fu-task-" + config.getTopic() + "-";
        Object result = evalScript(RScript.Mode.READ_WRITE, FORCE_RETRY_SCRIPT, forceRetrySha,
                RScript.ReturnType.INTEGER,
                List.of(
                        prefix + "message:" + taskId,
                        prefix + "blocking",
                        prefix + "pending"
                ),
                taskId);
        return result instanceof Long && (Long) result == 1L;
    }

    /**
     * Force a task to a terminal state (SUCCESS or FAIL). If status=SUCCESS and the
     * task has a callerId (FUNCTION mode), the result is routed to the caller's return deque.
     * If the task is in CALLBACK mode, the task is pushed to the callback deque.
     */
    public boolean forceComplete(String taskId, String status, JSONObject output, String errorMsg) {
        if (peek(taskId) == null) return false;
        if (!"SUCCESS".equals(status) && !"FAIL".equals(status)) {
            throw new IllegalArgumentException("status must be SUCCESS or FAIL");
        }
        String prefix = "fu-task-" + config.getTopic() + "-";

        // Get the task to discover callerId and mode
        FuTaskMessage msg = peek(taskId);
        String callerId = msg.getCallerId();
        String mode = msg.getMode();

        String returnDequeKey = "";
        String callbackDequeKey = "";
        if (callerId != null && !callerId.isEmpty()) {
            returnDequeKey = prefix + "return:" + callerId;
        }
        if ("CALLBACK".equals(mode)) {
            callbackDequeKey = prefix + "callback";
        }

        String outputJson = output != null ? output.toJSONString() : "";
        String err = errorMsg != null ? errorMsg : "";

        Object result = evalScript(RScript.Mode.READ_WRITE, FORCE_COMPLETE_SCRIPT, forceCompleteSha,
                RScript.ReturnType.INTEGER,
                List.of(
                        prefix + "message:" + taskId,
                        prefix + "blocking",
                        prefix + "pending",
                        prefix + "retry"
                ),
                taskId, status, outputJson, err,
                String.valueOf(System.currentTimeMillis()),
                callerId != null ? callerId : "",
                returnDequeKey,
                callbackDequeKey);
        return result instanceof Long && (Long) result == 1L;
    }

    /**
     * Delete a task from all queues and remove the message hash.
     * Returns the total number of removals across all queues.
     */
    public long delete(String taskId) {
        String prefix = "fu-task-" + config.getTopic() + "-";
        Object result = evalScript(RScript.Mode.READ_WRITE, DELETE_SCRIPT, deleteSha,
                RScript.ReturnType.INTEGER,
                List.of(
                        prefix + "message:" + taskId,
                        prefix + "blocking",
                        prefix + "timing",
                        prefix + "pending",
                        prefix + "retry",
                        prefix + "callback",
                        prefix + "callback-pending"
                ),
                taskId);
        long removed = result instanceof Long ? (Long) result : 0L;
        publishEvent("task.deleted", taskId, removed);
        return removed;
    }

    private void publishEvent(String type, String taskId, Object detail) {
        try {
            JSONObject evt = new JSONObject();
            evt.put("type", type);
            evt.put("topic", config.getTopic());
            evt.put("taskId", taskId);
            evt.put("timestamp", System.currentTimeMillis());
            if (detail != null) evt.put("detail", detail);
            RTopic topic = redisson.getTopic("retask4j-events", codec);
            topic.publish(evt.toJSONString());
        } catch (Exception e) {
            log.debug("Failed to publish event {} for {}", type, taskId, e);
        }
    }

    private Object evalScript(RScript.Mode mode, String scriptSource, AtomicReference<String> shaHolder,
                              RScript.ReturnType returnType, List<Object> keys, Object... args) {
        RScript script = redisson.getScript(codec);
        String cachedSha = shaHolder.get();
        if (cachedSha != null) {
            try {
                return script.evalSha(mode, cachedSha, returnType, keys, args);
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("NOSCRIPT")) {
                    shaHolder.set(null);
                } else {
                    throw e;
                }
            }
        }
        String result = script.scriptLoad(scriptSource);
        shaHolder.set(result);
        return script.evalSha(mode, result, returnType, keys, args);
    }

    private static String loadScript(String path) {
        try {
            return new String(FuTaskBase.class.getClassLoader().getResourceAsStream(path).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load script: " + path, e);
        }
    }

    static class ReTaskInfo extends FuTaskBase {
        public ReTaskInfo(RedissonClient redissonClient, String topic) {
            super(redissonClient, new FuTaskBaseConfig(topic));
        }

        public FuTaskMessage getMessageById(String taskId) {
            List<FuTaskMessage> list = getMessagesById(List.of(taskId));
            if (list != null && !list.isEmpty()) {
                return list.get(0);
            }
            return null;
        }
    }
}
