package fun.commons.retask4j.core.api;

import com.alibaba.fastjson2.JSONObject;
import fun.commons.retask4j.core.config.FuTaskBaseConfig;
import fun.commons.retask4j.core.internal.FuTaskBase;
import fun.commons.retask4j.core.message.FuTaskMessage;
import fun.commons.retask4j.core.message.FuTaskMode;
import fun.commons.retask4j.core.util.TtlUtils;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.redisson.api.RScript;
import org.redisson.client.codec.Codec;
import org.redisson.codec.JsonJacksonCodec;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One-shot task submission service. Unlike {@link FuTaskCaller} which is a long-lived
 * producer with its own return-future cache, this service is designed for stateless
 * request/response flows (e.g. an HTTP submission API).
 *
 * <p>Use cases:
 * <ul>
 *   <li>Non-Java clients (Python, Go, Node.js) submitting tasks via HTTP gateway</li>
 *   <li>Stateless REST services that need to enqueue tasks without owning a long-running caller</li>
 *   <li>One-off jobs from CLI tools, scripts, or batch processors</li>
 * </ul>
 *
 * <p>Each {@link #submit} call creates a fresh {@link FuTaskMessage}, applies the requested
 * config, and pushes it to Redis atomically. No thread pools, no return-future cache, no
 * persistence between calls — every invocation is independent.
 */
@Slf4j
public class FuTaskSubmitter {

    private static final String PUSH_SCRIPT = loadScript("scripts/push_task_message_deque_batch.lua");

    private final RedissonClient redisson;
    private final FuTaskBaseConfig config;
    private final Codec codec = new JsonJacksonCodec();
    private final AtomicReference<String> pushSha = new AtomicReference<>();

    public FuTaskSubmitter(RedissonClient redisson, FuTaskBaseConfig config) {
        this.redisson = redisson;
        this.config = config;
    }

    public FuTaskSubmitter(RedissonClient redisson, String topic) {
        this(redisson, new FuTaskBaseConfig(topic));
    }

    /**
     * Build a task message from a request body. Used by both the in-process submit() path
     * and external HTTP gateways that want to construct the message themselves.
     */
    public static FuTaskMessage build(FuTaskBaseConfig config, SubmitRequest req) {
        if (req.id == null || req.id.isEmpty()) {
            req.id = UUID.randomUUID().toString().replace("-", "");
        }
        FuTaskMessage msg = new FuTaskMessage(config.getTopic(), req.id);
        msg.setInput(req.input);
        msg.setMode(req.mode == null ? FuTaskMode.NORMAL : req.mode);
        msg.setCallerId(req.callerId);
        msg.setStrategy(req.strategy);
        msg.setTag(req.tag);
        msg.setRetryPlan(req.retryPlan == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(req.retryPlan));
        msg.setDelayTime(req.delayTime == null ? 0 : req.delayTime);
        msg.setExecuteExpire(req.executeExpire == null ? 3600 : req.executeExpire);
        msg.setResultExpire(req.resultExpire == null ? 0 : req.resultExpire);
        if (req.callbackUrl != null && !req.callbackUrl.isEmpty()) {
            msg.setCallerId(msg.getCallerId() == null ? "external-" + req.id : msg.getCallerId());
        }
        int ttlBuffer = TtlUtils.computeTtlBuffer(msg.getRetryPlan(), msg.getExecuteExpire());
        msg.setTtlBuffer(ttlBuffer);
        return msg;
    }

    /**
     * Build, validate, and atomically push a single task to Redis. Returns the persisted
     * task on success, or throws if validation or push fails.
     */
    public FuTaskMessage submit(SubmitRequest req) {
        if (req == null) throw new IllegalArgumentException("request must not be null");
        FuTaskMessage msg = build(config, req);
        if (msg.getMode() == null) msg.setMode(FuTaskMode.NORMAL);
        int pushed = doPush(List.of(msg));
        if (pushed == 0) {
            throw new RuntimeException("Failed to push task to Redis");
        }
        return msg;
    }

    /**
     * Push multiple tasks atomically. Returns the count of successfully pushed tasks.
     */
    public int submitAll(List<FuTaskMessage> messages) {
        if (messages == null || messages.isEmpty()) return 0;
        return doPush(messages);
    }

    private int doPush(List<FuTaskMessage> messages) {
        if (messages.isEmpty()) return 0;
        try {
            String[] items = new String[messages.size()];
            for (int i = 0; i < messages.size(); i++) {
                items[i] = com.alibaba.fastjson2.JSON.toJSONString(messages.get(i).toRequestMap());
            }
            String prefix = "fu-task-" + config.getTopic() + "-";
            String workingKey = prefix + "blocking";
            String timingKey = prefix + "timing";
            Object result = evalScript(RScript.Mode.READ_WRITE, PUSH_SCRIPT, pushSha,
                    RScript.ReturnType.INTEGER,
                    List.of(prefix + "message:", workingKey, timingKey),
                    (Object[]) items);
            return result instanceof Long ? ((Long) result).intValue() : 0;
        } catch (Exception e) {
            log.error("submit push error", e);
            return 0;
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

    /**
     * Request body for {@link #submit}. {@code topic} is required and is read by
     * the HTTP gateway from the JSON body (rather than from the URL path so the gateway
     * can route to a single endpoint). All other fields are optional.
     */
    public static class SubmitRequest {
        public String topic;
        public String id;
        public String mode;
        public JSONObject input;
        public String callerId;
        public String strategy;
        public String tag;
        public List<Integer> retryPlan;
        public Integer delayTime;
        public Integer executeExpire;
        public Integer resultExpire;
        public String callbackUrl;

        /** Helper for HTTP gateway to extract and validate the topic. */
        public String topicFromBody() {
            return topic;
        }
    }
}
