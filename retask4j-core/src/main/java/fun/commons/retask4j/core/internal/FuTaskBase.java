package fun.commons.retask4j.core.internal;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.alibaba.fastjson2.util.TypeUtils;

import fun.commons.retask4j.core.config.FuTaskBaseConfig;
import fun.commons.retask4j.core.config.FuTaskCallConfig;
import fun.commons.retask4j.core.message.FuTaskMessage;
import fun.commons.retask4j.core.message.FuTaskMode;
import fun.commons.retask4j.core.message.FuTaskStatus;

import org.redisson.api.*;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class FuTaskBase {


    private final Codec codec = StringCodec.INSTANCE;

    protected Codec getCodec() { return codec; }

    protected final FuTaskBaseConfig config;

    protected final String topic;

    protected final RedissonClient redissonClient;

    protected final String keyPrefix;
    protected final String messagePrefix;
    protected final String funcPrefix;


    protected final RBlockingDeque<String> workingDeque;
    protected final RScoredSortedSet<String> pendingSet;
    protected final RScoredSortedSet<String> timingSet;
    protected final RScoredSortedSet<String> retrySet;
    protected final RScoredSortedSet<String> callbackPendingSet;
    protected final RBlockingDeque<String> callbackDeque;


    public FuTaskBase(RedissonClient redissonClient, FuTaskBaseConfig  config) {

        this.redissonClient = redissonClient;
        this.config = config;
        this.topic = this.config.getTopic();

        this.keyPrefix = "fu-task-{%s}".formatted(topic);
        this.messagePrefix = this.keyPrefix + "-message:";
        this.funcPrefix = this.keyPrefix + "-return:";

        this.workingDeque = redissonClient.getBlockingDeque(keyPrefix + "-blocking", codec);
        this.timingSet = redissonClient.getScoredSortedSet(keyPrefix + "-timing", codec);
        this.pendingSet = redissonClient.getScoredSortedSet(keyPrefix + "-pending", codec);
        this.retrySet = redissonClient.getScoredSortedSet(keyPrefix + "-retry", codec);
        this.callbackDeque = redissonClient.getBlockingDeque(keyPrefix + "-callback", codec);
        this.callbackPendingSet = redissonClient.getScoredSortedSet(keyPrefix + "-callback-pending", codec);

    }


    private static String loadScript(String path) {
        try (InputStream is = FuTaskBase.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Lua script not found on classpath: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load Lua script: " + path, e);
        }
    }

    /**
     * Execute a Lua script using EVALSHA with EVAL fallback.
     * Caches the SHA on first successful load to avoid sending full script text on every call.
     */
    private Object evalScript(RScript.Mode mode, String scriptSource, AtomicReference<String> shaHolder,
                               RScript.ReturnType returnType, List<Object> keys, Object... args) {
        RScript script = redissonClient.getScript(codec);
        // Try EVALSHA first if we have a cached SHA
        String cachedSha = shaHolder.get();
        if (cachedSha != null) {
            try {
                return script.evalSha(mode, cachedSha, returnType, keys, args);
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg != null && msg.contains("NOSCRIPT")) {
                    // SHA expired in Redis — reload script WITHOUT executing, then retry EVALSHA
                    shaHolder.set(null);
                    log.debug("Script SHA expired, reloading: {}", cachedSha);
                    try {
                        String newSha = script.scriptLoad(scriptSource);
                        shaHolder.set(newSha);
                        return script.evalSha(mode, newSha, returnType, keys, args);
                    } catch (Exception loadEx) {
                        // scriptLoad or retry evalSha failed — fall through to full EVAL
                        log.debug("Failed to reload script SHA, falling back to EVAL", loadEx);
                    }
                } else {
                    // Transient errors (timeout, failover) should NOT invalidate the SHA cache
                    throw e;
                }
            }
        }
        // Load script via EVAL and cache SHA
        Object result = script.eval(mode, scriptSource, returnType, keys, args);
        try {
            String newSha = script.scriptLoad(scriptSource);
            shaHolder.set(newSha);
        } catch (Exception e) {
            log.debug("Failed to cache script SHA, will use EVAL on next call", e);
        }
        return result;
    }


    public static int computeTtlBuffer(List<Integer> retryPlan, int executeExpire) {
        return fun.commons.retask4j.core.util.TtlUtils.computeTtlBuffer(retryPlan, executeExpire);
    }

    protected boolean sleep(long sleepTimeMs) {
        if (sleepTimeMs > 0) {
            try {
                Thread.sleep(sleepTimeMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return true;
            }
        }
        return false;
    }

    //-- Lua script for moving elements from sorted set to list
    private static final String removeStoreSetToListScript = loadScript("scripts/remove_store_set_to_list.lua");
    private final AtomicReference<String> removeStoreSetToListSha = new AtomicReference<>();

    protected int doRemoveStoreSetToListScript(String scoreSetName, String listName, int maxCount) {
        return doRemoveStoreSetToListScript(scoreSetName, listName, maxCount, true);
    }

    protected int doRemoveStoreSetToListScript(String scoreSetName, String listName, int maxCount, boolean resetStatus) {
        try {
            Object result = evalScript(RScript.Mode.READ_WRITE, removeStoreSetToListScript, removeStoreSetToListSha,
                    RScript.ReturnType.INTEGER,
                    List.<Object>of(scoreSetName, listName, messagePrefix),
                    String.valueOf(maxCount),
                    resetStatus ? "1" : "0");
            return TypeUtils.toIntValue(result);
        } catch (Exception e) {
            log.error("Lua script execution error", e);
            return 0;
        }
    }


    // Immediately fetch the specified number of elements, add them to the sorted set, and return the list of removed elements
    private static final String getTaskMessageForWorkScript = loadScript("scripts/get_task_messages_for_work.lua");
    private final AtomicReference<String> getTaskMessageForWorkSha = new AtomicReference<>();

    protected List<Map<String,String>> doGetTaskMessagesForWorkScript(String workListName, String pendingSetName, int maxCount, int pendingTimeout, String messagePrefix, List<String> fields, boolean setPendingStatus) {
        try {
            @SuppressWarnings("unchecked")
            List<String> result = (List<String>) evalScript(RScript.Mode.READ_WRITE, getTaskMessageForWorkScript, getTaskMessageForWorkSha,
                    RScript.ReturnType.MULTI,
                    List.<Object>of(workListName, pendingSetName, messagePrefix),
                    String.valueOf(maxCount),
                    String.valueOf(pendingTimeout),
                    setPendingStatus ? "1" : "0",
                    JSON.toJSONString(fields));
            return result.stream()
                    .filter(Objects::nonNull)
                    .map(str -> {
                        try {
                            return JSONObject.parseObject(str, new TypeReference<Map<String,String>>() {});
                        } catch (Exception e) {
                            log.warn("Failed to parse task message from script result: {}", str, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.error("Lua script execution error in doGetTaskMessagesForWorkScript", e);
            return List.of();
        }
    }

    protected List<FuTaskMessage> getMessagesForWork(int maxCount, int pendingTimeout) {
        List<Map<String,String>> list =
                doGetTaskMessagesForWorkScript(
                        workingDeque.getName(),
                        pendingSet.getName(),
                        maxCount,
                        pendingTimeout,
                        messagePrefix,
                        FuTaskMessage.requestFields,
                        true);
        List<FuTaskMessage> result = new ArrayList<>();
        for (Map<String,String> item : list) {
            FuTaskMessage msg = FuTaskMessage.fromStringMap(item);
            if (msg != null) {
                result.add(msg);
            } else {
                // Corrupted message (null id) — remove from pendingSet to break re-delivery loop
                String id = item.get("id");
                if (id != null) {
                    pendingSet.remove(id);
                    log.warn("Removed corrupted message from pendingSet: {}", id);
                }
            }
        }
        return result;
    }


    protected List<FuTaskMessage> getMessagesForCallback(int maxCount, int pendingTimeout) {
        List<Map<String,String>> list =
                doGetTaskMessagesForWorkScript(
                        callbackDeque.getName(),
                        callbackPendingSet.getName(),
                        maxCount,
                        pendingTimeout,
                        messagePrefix,
                        FuTaskMessage.allFields,
                        false);
        List<FuTaskMessage> result = new ArrayList<>();
        for (Map<String,String> item : list) {
            FuTaskMessage msg = FuTaskMessage.fromStringMap(item);
            if (msg != null) {
                result.add(msg);
            } else {
                String id = item.get("id");
                if (id != null) {
                    callbackPendingSet.remove(id);
                    log.warn("Removed corrupted callback message from callbackPendingSet: {}", id);
                }
            }
        }
        return result;
    }


    private static final String getTaskMessagesByIdScript = loadScript("scripts/get_task_messages_by_id.lua");
    private final AtomicReference<String> getTaskMessagesByIdSha = new AtomicReference<>();

    protected List<FuTaskMessage> getMessagesById(List<String> ids) {
        try {
            @SuppressWarnings("unchecked")
            List<String> result = (List<String>) evalScript(RScript.Mode.READ_ONLY, getTaskMessagesByIdScript, getTaskMessagesByIdSha,
                    RScript.ReturnType.MULTI,
                    List.<Object>of(messagePrefix),
                    ids.toArray());
            List<Map<String, String>> list = result.stream()
                    .filter(Objects::nonNull)
                    .map(str -> {
                        try {
                            return JSONObject.parseObject(str, new TypeReference<Map<String, String>>() {});
                        } catch (Exception e) {
                            log.warn("Failed to parse task message by id: {}", str, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();
            return list.stream().map(item -> FuTaskMessage.fromStringMap(item)).filter(Objects::nonNull).toList();
        } catch (Exception e) {
            log.error("getMessagesById error", e);
            throw e;
        }
    }


    private static final String pushTaskMessageDequeBatchScript = loadScript("scripts/push_task_message_deque_batch.lua");
    private final AtomicReference<String> pushTaskMessageDequeBatchSha = new AtomicReference<>();

    private int doPushTaskMessageDequeBatchScript(List<FuTaskMessage> taskMessages) {
        try {
            List<FuTaskMessage> valid = filterNullId(taskMessages);
            if (valid.isEmpty()) return 0;
            String[] items  =  new String[valid.size()];
            for (int i = 0; i < valid.size(); i++) {
                items[i] = JSON.toJSONString(valid.get(i).toRequestMap());
            }
            Object result = evalScript(RScript.Mode.READ_WRITE, pushTaskMessageDequeBatchScript, pushTaskMessageDequeBatchSha,
                    RScript.ReturnType.INTEGER,
                    List.<Object>of(messagePrefix, workingDeque.getName(), timingSet.getName()),
                    items);
            return TypeUtils.toIntValue(result);
        } catch (Exception e) {
            log.error("doPushTaskMessageDequeBatchScript error", e);
            return 0;
        }
    }



    protected int send(FuTaskMessage... messages) {
        return send(List.of(messages));
    }

    protected int send(List<FuTaskMessage> messages) {
        return doPushTaskMessageDequeBatchScript(messages);
    }

    protected int retry(FuTaskMessage... taskMessages) {
        return retry(List.of(taskMessages));
    }
    protected int retry(List<FuTaskMessage> taskMessages) {
        return doRetryBatchScript(taskMessages);
    }

    private static final String retryBatchScript = loadScript("scripts/retry_batch.lua");
    private final AtomicReference<String> retryBatchSha = new AtomicReference<>();

    protected int doRetryBatchScript(List<FuTaskMessage> taskMessages) {
        try {
            List<FuTaskMessage> valid = filterNullId(taskMessages);
            if (valid.isEmpty()) return 0;
            String[] items =  new String[valid.size()];
            for (int i = 0; i < valid.size(); i++) {
                FuTaskMessage taskMessage = valid.get(i);
                int newRetryTimes = taskMessage.getRetryTimes() + 1;
                List<Integer> plan = taskMessage.getRetryPlan();
                int delay = (plan != null && newRetryTimes - 1 < plan.size()) ? plan.get(newRetryTimes - 1) : 0;
                if (delay < 1) delay = 1; // Minimum 1s to prevent CPU-burning hot loop
                if (delay > FuTaskBaseConfig.MAX_EXPIRE_SECONDS) delay = (int) FuTaskBaseConfig.MAX_EXPIRE_SECONDS;
                Map<String, String> item = taskMessage.toRetryFullMap();
                item.put("retryTimes", String.valueOf(newRetryTimes));
                item.put("status", FuTaskStatus.WAITING);
                item.put("retryDelay", String.valueOf(delay));
                item.put("executeExpire", String.valueOf(taskMessage.getExecuteExpire()));
                items[i] = JSON.toJSONString(item);
            }
            Object result = evalScript(RScript.Mode.READ_WRITE, retryBatchScript, retryBatchSha,
                    RScript.ReturnType.INTEGER,
                    List.<Object>of(retrySet.getName(), workingDeque.getName(), messagePrefix, pendingSet.getName()),
                    items);
            int count = TypeUtils.toIntValue(result);
            if (count < valid.size()) {
                log.warn("retry_batch dropped {} tasks (hash expired before retry)", valid.size() - count);
            }
            return count;
        } catch (Exception e) {
            log.error("doRetryBatchScript error", e);
            return 0;
        }
    }

    protected int complete(FuTaskMessage... taskMessages) {
        return complete(List.of(taskMessages));
    }

    protected int complete(List<FuTaskMessage> messages) {
        return doCompleteBatchScript(messages);
    }


    private static final String completeBatchScript = loadScript("scripts/complete_batch.lua");
    private final AtomicReference<String> completeBatchSha = new AtomicReference<>();

    private int doCompleteBatchScript(List<FuTaskMessage> taskMessages) {
        try {
            List<FuTaskMessage> valid = filterNullId(taskMessages);
            if (valid.isEmpty()) return 0;
            String[] items =  new String[valid.size()];
            for (int i = 0; i < valid.size(); i++) {
                FuTaskMessage msg = valid.get(i);
                if (FuTaskMode.FUNCTION.equals(msg.getMode()) && msg.getCallerId() == null) {
                    log.error("FUNCTION-mode task {} has null callerId, result will not be routed", msg.getId());
                }
                items[i] = JSON.toJSONString(msg.toCompleteMap());
            }
            Object result = evalScript(RScript.Mode.READ_WRITE, completeBatchScript, completeBatchSha,
                    RScript.ReturnType.INTEGER,
                    List.<Object>of(messagePrefix, pendingSet.getName(), funcPrefix, callbackDeque.getName()),
                    items);
            int count = TypeUtils.toIntValue(result);
            if (count < valid.size()) {
                log.warn("complete_batch dropped {} results (hash expired before completion)", valid.size() - count);
            }
            return count;
        } catch (Exception e) {
            log.error("doCompleteBatchScript error", e);
            return 0;
        }
    }


    private static final String setCallbackBatchScript = loadScript("scripts/set_callback_batch.lua");
    private final AtomicReference<String> setCallbackBatchSha = new AtomicReference<>();

    protected int doSetCallbackBatchScript(List<FuTaskMessage> taskMessages,int pendingTimeout) {
        try {
            List<FuTaskMessage> valid = filterNullId(taskMessages);
            if (valid.isEmpty()) return 0;
            String[] items =  new String[valid.size() + 1];
            items[0] = String.valueOf(pendingTimeout);
            for (int i = 0; i < valid.size(); i++) {
                items[i+1] = JSON.toJSONString(valid.get(i).toCallbackMap());
            }

            Object result = evalScript(RScript.Mode.READ_WRITE, setCallbackBatchScript, setCallbackBatchSha,
                    RScript.ReturnType.INTEGER,
                    List.<Object>of(messagePrefix, callbackPendingSet.getName()),
                    items);
            int count = TypeUtils.toIntValue(result);
            if (count == 0 && !valid.isEmpty()) {
                log.warn("doSetCallbackBatchScript returned 0 for {} messages (hashes may have expired)", valid.size());
            }
            return count;
        } catch (Exception e) {
            log.error("doSetCallbackBatchScript error", e);
            return 0;
        }
    }

    private static List<FuTaskMessage> filterNullId(List<FuTaskMessage> messages) {
        List<FuTaskMessage> valid = new ArrayList<>(messages.size());
        for (FuTaskMessage msg : messages) {
            String id = msg.getId();
            if (id == null || id.isEmpty()) {
                log.error("Skipping task with null/empty id in batch script call");
            } else {
                valid.add(msg);
            }
        }
        return valid;
    }

    protected List<String> pollReturnMessageIds(RBlockingDeque<String> returnDeque, int maxCount){
        List<String> result = returnDeque.poll(maxCount);
        return result != null ? result : List.of();
    }

    protected JSONObject getTaskCountInfo(){
        JSONObject result = new JSONObject();
        result.put("working", safeSize(workingDeque));
        result.put("pending", safeSize(pendingSet));
        result.put("timing", safeSize(timingSet));
        result.put("retry", safeSize(retrySet));
        result.put("callback-working", safeSize(callbackDeque));
        result.put("callback-pending", safeSize(callbackPendingSet));
        return result;
    }

    private long safeSize(Object redisObject) {
        try {
            if (redisObject instanceof RBlockingDeque<?> deque) return deque.size();
            if (redisObject instanceof RScoredSortedSet<?> set) return set.size();
            return -1;
        } catch (Exception e) {
            log.debug("Failed to get size for {}", redisObject, e);
            return -1;
        }
    }

    /**
     * Scans Redis for all active task topics. Returns a sorted list of unique topic names
     * by inspecting the working-deque keys (pattern: fu-task-{topic}-blocking).
     *
     * <p>Uses Redis SCAN to avoid blocking on large keyspaces.
     */
    public static List<String> listTopics(RedissonClient redisson) {
        java.util.Set<String> topics = new java.util.TreeSet<>();
        String pattern = "fu-task-*-blocking";
        Iterable<String> keys;
        try {
            keys = redisson.getKeys().getKeysByPattern(pattern, 100);
        } catch (Exception e) {
            log.warn("Failed to scan Redis keys for topics", e);
            return java.util.List.of();
        }
        for (String key : keys) {
            // key format: fu-task-{topic}-blocking
            int prefixEnd = "fu-task-".length();
            int suffixStart = key.lastIndexOf("-blocking");
            if (prefixEnd > 0 && suffixStart > prefixEnd) {
                topics.add(key.substring(prefixEnd, suffixStart));
            }
        }
        return new java.util.ArrayList<>(topics);
    }

}
