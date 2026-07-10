package fun.commons.retask4j.core.message;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.util.TypeUtils;
import fun.commons.retask4j.core.config.FuTaskBaseConfig;
import fun.commons.retask4j.core.config.FuTaskCallConfig;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;


@Getter
@Slf4j
public final class FuTaskMessage {

    @Target({ElementType.FIELD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    private @interface FuTag {
        String[] value() default {};
    }

    // Message ID
    @FuTag({"request", "retry", "complete","callback"})
    @Setter(AccessLevel.NONE)
    private String id;

    public void setId(String id) {
        this.id = id;
    }

    // Message topic
    @FuTag({"request", "complete", "callback"})
    @Setter(AccessLevel.NONE)
    private String topic;

    public void setTopic(String topic) {
        this.topic = topic;
    }

    // Message tag
    @FuTag({"request"})
    @Setter(AccessLevel.NONE)
    private String tag = null;

    private static final int MAX_TAG_LENGTH = 128;

    public void setTag(String tag) {
        if (tag != null && tag.length() > MAX_TAG_LENGTH) {
            throw new IllegalArgumentException("tag must not exceed " + MAX_TAG_LENGTH + " characters: " + tag.length());
        }
        this.tag = tag;
    }

    // Strategy
    @FuTag({"request"})
    @Setter(AccessLevel.NONE)
    private String strategy = null;

    private static final int MAX_STRATEGY_LENGTH = 128;

    public void setStrategy(String strategy) {
        if (strategy != null && strategy.length() > MAX_STRATEGY_LENGTH) {
            throw new IllegalArgumentException("strategy must not exceed " + MAX_STRATEGY_LENGTH + " characters: " + strategy.length());
        }
        this.strategy = strategy;
    }

    // Task creation time (epoch milliseconds)
    @FuTag({"request"})
    private long createTime = System.currentTimeMillis();

    // Delayed message, consumption delay in seconds; 0 means immediate
    @FuTag({"request"})
    @Setter(AccessLevel.NONE)
    private int delayTime = 0;

    public void setDelayTime(int delayTime) {
        if (delayTime < 0) {
            throw new IllegalArgumentException("delayTime must not be negative: " + delayTime);
        }
        if (delayTime > FuTaskBaseConfig.MAX_EXPIRE_SECONDS) {
            throw new IllegalArgumentException("delayTime must not exceed " + FuTaskBaseConfig.MAX_EXPIRE_SECONDS + " seconds: " + delayTime);
        }
        this.delayTime = delayTime;
    }

    // Retry plan (seconds) [1,3]
    @FuTag({"request"})
    @Setter(AccessLevel.NONE)
    private List<Integer> retryPlan = new ArrayList<>();

    public List<Integer> getRetryPlan() {
        return Collections.unmodifiableList(retryPlan);
    }

    public void setRetryPlan(List<Integer> retryPlan) {
        if (retryPlan != null) {
            if (retryPlan.size() > 20) {
                throw new IllegalArgumentException("retryPlan must not exceed 20 entries: " + retryPlan.size());
            }
            for (Integer d : retryPlan) {
                if (d == null) {
                    throw new IllegalArgumentException("retryPlan must not contain null elements");
                }
                if (d < 1) {
                    throw new IllegalArgumentException("retryPlan delay must be at least 1 second: " + d);
                }
                if (d > FuTaskBaseConfig.MAX_EXPIRE_SECONDS) {
                    throw new IllegalArgumentException("retryPlan delay must not exceed " + FuTaskBaseConfig.MAX_EXPIRE_SECONDS + " seconds: " + d);
                }
            }
        }
        this.retryPlan = retryPlan != null ? new ArrayList<>(retryPlan) : new ArrayList<>();
    }

    // Execution expiration in seconds, default 1 hour
    @FuTag({"request"})
    @Setter(AccessLevel.NONE)
    private int executeExpire = 3600;

    public void setExecuteExpire(int executeExpire) {
        if (executeExpire < 1) {
            throw new IllegalArgumentException("executeExpire must be at least 1 second: " + executeExpire);
        }
        if (executeExpire > FuTaskBaseConfig.MAX_EXPIRE_SECONDS) {
            throw new IllegalArgumentException("executeExpire must not exceed " + FuTaskBaseConfig.MAX_EXPIRE_SECONDS + " seconds: " + executeExpire);
        }
        this.executeExpire = executeExpire;
    }

    //TTL buffer for retry lifecycle (sum of retryPlan delays + execution windows), computed at send time
    @FuTag({"request"})
    @Setter(AccessLevel.NONE)
    private int ttlBuffer = 0;

    public void setTtlBuffer(int ttlBuffer) {
        if (ttlBuffer < 0) {
            throw new IllegalArgumentException("ttlBuffer must not be negative: " + ttlBuffer);
        }
        this.ttlBuffer = ttlBuffer;
    }

    // Result retention time in seconds, default 0 (no retention)
    @FuTag({"request","complete","callback"})
    @Setter(AccessLevel.NONE)
    private int resultExpire = 0;

    public void setResultExpire(int resultExpire) {
        if (resultExpire < 0) {
            throw new IllegalArgumentException("resultExpire must not be negative: " + resultExpire);
        }
        if (resultExpire > FuTaskBaseConfig.MAX_EXPIRE_SECONDS) {
            throw new IllegalArgumentException("resultExpire must not exceed " + FuTaskBaseConfig.MAX_EXPIRE_SECONDS + " seconds: " + resultExpire);
        }
        this.resultExpire = resultExpire;
    }

    //
    @FuTag({"request"})
    @Setter(AccessLevel.NONE)
    private JSONObject input;

    public void setInput(JSONObject input) {
        this.input = input;
    }

    //
    @FuTag({"request", "complete", "callback"})
    @Setter(AccessLevel.NONE)
    private JSONObject extInfo = new JSONObject();

    public void setExtInfo(JSONObject extInfo) {
        this.extInfo = extInfo != null ? extInfo : new JSONObject();
    }

    // Task schedule time (epoch seconds, used as Redis sorted-set score)
    // Overwritten by Lua scripts: push_task_message_deque_batch.lua sets currentTime + delayTime
    // Java initializer is only used before the message is persisted to Redis
    @FuTag({"request", "retry"})
    @Setter(AccessLevel.NONE)
    private long scheduleTime = 0;

    public void setScheduleTime(long scheduleTime) {
        if (scheduleTime < 0) {
            throw new IllegalArgumentException("scheduleTime must not be negative: " + scheduleTime);
        }
        this.scheduleTime = scheduleTime;
    }

    // Number of retries, default 0
    @FuTag({"request", "retry"})
    @Setter(AccessLevel.NONE)
    private int retryTimes = 0;

    public void setRetryTimes(int retryTimes) {
        if (retryTimes < 0) {
            throw new IllegalArgumentException("retryTimes must not be negative: " + retryTimes);
        }
        this.retryTimes = retryTimes;
    }

    // Current retry delay in seconds = retryPlan[retryTimes]
    @FuTag({"request", "retry"})
    @Setter(AccessLevel.NONE)
    private int retryDelay= 0;

    public void setRetryDelay(int retryDelay) {
        if (retryDelay < 0) {
            throw new IllegalArgumentException("retryDelay must not be negative: " + retryDelay);
        }
        this.retryDelay = retryDelay;
    }

    // Message status, default waiting,
    // waiting: waiting for consumption, including
    // pending: waiting for consumption confirmation
    // success: consumption succeeded
    // fail: consumption failed
    @FuTag({"request", "retry", "complete", "callback"})
    @Setter(AccessLevel.NONE)
    private String status = FuTaskStatus.WAITING;

    private static final Set<String> VALID_STATUSES = Set.of(FuTaskStatus.WAITING, FuTaskStatus.PENDING, FuTaskStatus.SUCCESS, FuTaskStatus.FAIL);

    public void setStatus(String status) {
        if (status != null && !VALID_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Invalid status value: " + status + ", must be one of " + VALID_STATUSES);
        }
        this.status = status;
    }

    // Actual execution time (epoch milliseconds)
    @FuTag({"complete","callback"})
    @Setter(AccessLevel.NONE)
    private long executeTime;

    public void setExecuteTime(long executeTime) {
        this.executeTime = executeTime;
    }

    // Actual completion time (epoch milliseconds)
    @FuTag({"complete","callback"})
    @Setter(AccessLevel.NONE)
    private long completeTime;

    public void setCompleteTime(long completeTime) {
        this.completeTime = completeTime;
    }

    // Error message, if any
    @FuTag({"complete","callback"})
    @Setter(AccessLevel.NONE)
    private String error;

    private static final int MAX_ERROR_LENGTH = 4096;

    public void setError(String error) {
        this.error = error != null && error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
    }

    // Output
    @FuTag({"complete", "callback"})
    @Setter(AccessLevel.NONE)
    private JSONObject output;

    public void setOutput(JSONObject output) {
        this.output = output;
    }

    @FuTag({"request","complete","callback"})
    @Setter(AccessLevel.NONE)
    private String callerId;

    public void setCallerId(String callerId) {
        if (callerId != null) {
            if (callerId.isEmpty()) {
                throw new IllegalArgumentException("callerId must not be empty");
            }
            if (callerId.length() > 256) {
                throw new IllegalArgumentException("callerId must not exceed 256 characters: " + callerId.length());
            }
            if (callerId.contains(":") || callerId.contains("{") || callerId.contains("}")) {
                throw new IllegalArgumentException("callerId contains unsafe characters for Redis keys: " + callerId);
            }
            for (int i = 0; i < callerId.length(); i++) {
                char c = callerId.charAt(i);
                if (c < ' ' || c == 127) {
                    throw new IllegalArgumentException("callerId must not contain control characters");
                }
            }
        }
        this.callerId = callerId;
    }

    // Message mode
    @FuTag({"request","complete","callback"})
    @Setter(AccessLevel.NONE)
    private String mode = FuTaskMode.NORMAL;

    private static final Set<String> VALID_MODES = Set.of(FuTaskMode.NORMAL, FuTaskMode.FUNCTION, FuTaskMode.CALLBACK);

    public void setMode(String mode) {
        if (mode != null && !VALID_MODES.contains(mode)) {
            throw new IllegalArgumentException("Invalid mode value: " + mode + ", must be one of " + VALID_MODES);
        }
        this.mode = mode;
    }

    @FuTag({"complete", "callback"})
    @Setter(AccessLevel.NONE)
    private int callbackRetryTimes = 0;

    public void setCallbackRetryTimes(int callbackRetryTimes) {
        if (callbackRetryTimes < 0) {
            throw new IllegalArgumentException("callbackRetryTimes must not be negative: " + callbackRetryTimes);
        }
        this.callbackRetryTimes = callbackRetryTimes;
    }

    @FuTag({"complete", "callback"})
    @Setter(AccessLevel.NONE)
    private String callbackStatus = FuTaskStatus.WAITING;

    private static final Set<String> VALID_CALLBACK_STATUSES = Set.of(FuTaskStatus.WAITING, FuTaskStatus.SUCCESS, FuTaskStatus.FAIL);

    public void setCallbackStatus(String callbackStatus) {
        if (callbackStatus != null && !VALID_CALLBACK_STATUSES.contains(callbackStatus)) {
            throw new IllegalArgumentException("Invalid callbackStatus value: " + callbackStatus);
        }
        this.callbackStatus = callbackStatus;
    }

    @FuTag({"callback"})
    @Setter(AccessLevel.NONE)
    private String callbackError;

    public void setCallbackError(String callbackError) {
        this.callbackError = callbackError != null && callbackError.length() > MAX_ERROR_LENGTH ? callbackError.substring(0, MAX_ERROR_LENGTH) : callbackError;
    }

    private FuTaskMessage(){}

    public FuTaskMessage(String topic, String id) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be null or blank");
        }
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("id must not be null or empty");
        }
        if (id.length() > 256) {
            throw new IllegalArgumentException("id must not exceed 256 characters: " + id.length());
        }
        if (id.contains(":") || id.contains("{") || id.contains("}")) {
            throw new IllegalArgumentException("id must not contain ':', '{', or '}' (conflicts with Redis key syntax): " + id);
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c < ' ' || c == 127) {
                throw new IllegalArgumentException("id must not contain control characters");
            }
        }
        this.topic = topic;
        this.id = id;
    }

    public Map<String, String> toRequestMap() {
        return toStringMap(requestFieldSet);
    }

    public Map<String, String> toRetryMap() {
        return toStringMap(retryFieldSet);
    }

    public Map<String, String> toRetryFullMap() {
        Map<String, String> map = new LinkedHashMap<>(toStringMap(requestFieldSet));
        map.putAll(toStringMap(retryFieldSet));
        return map;
    }

    public Map<String, String> toCompleteMap() {
        return toStringMap(completeFieldSet);
    }
    public Map<String, String> toCallbackMap() {
        return toStringMap(callbackFieldSet);
    }

    private static final List<Field> INSTANCE_FIELDS;
    static {
        Field[] declared = FuTaskMessage.class.getDeclaredFields();
        List<Field> fields = new ArrayList<>();
        for (Field f : declared) {
            if (!Modifier.isStatic(f.getModifiers())) {
                try {
                    f.setAccessible(true);
                } catch (InaccessibleObjectException e) {
                    throw new IllegalStateException(
                        "Cannot access field '" + f.getName() + "' in FuTaskMessage. " +
                        "When running on the module path, add '--add-opens fun.commons.retask4j.core.message/fun.commons.retask4j.core.message=ALL-UNNAMED' " +
                        "or open the package in your module-info.java", e);
                }
                fields.add(f);
            }
        }
        INSTANCE_FIELDS = List.copyOf(fields);
    }

    private Map<String, String> toStringMap(Set<String> fields) {
        Map<String, String> ret = new LinkedHashMap<>();
        for (Field field : INSTANCE_FIELDS) {
            String key = field.getName();
            if (fields.contains(key)) {
                try {
                    Object value = field.get(this);
                    if (Objects.nonNull(value)) {
                        ret.put(key, TypeUtils.cast(value, String.class));
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return ret;
    }

    public static FuTaskMessage fromStringMap(Map<String, String> item) {
        FuTaskMessage taskMessage = new FuTaskMessage();
        for (Field field : INSTANCE_FIELDS) {
            String key = field.getName();
            String value = item.get(key);
            if (item.containsKey(key) && Objects.nonNull(value)) {
                try {
                    Object obj = TypeUtils.cast(value, field.getType());
                    field.set(taskMessage, obj);
                } catch (Exception e) {
                    // Skip corrupted fields rather than failing the entire message
                    log.warn("Failed to parse field '{}' with value '{}'", key, value);
                }
            }
        }
        // Normalize fields that have setter validation — reflection bypasses setters.
        // Wrap in try-catch so a single corrupted field doesn't crash the entire batch deserialization.
        try {
        if (!taskMessage.retryPlan.isEmpty()) {
            // TypeUtils.cast with raw List.class may produce JSONArray with Long/BigDecimal
            // elements; normalize to ArrayList<Integer> to prevent ClassCastException
            List<Integer> normalized = new ArrayList<>(taskMessage.retryPlan.size());
            for (Object elem : taskMessage.retryPlan) {
                int val = ((Number) elem).intValue();
                if (val < 1) {
                    log.warn("Normalizing invalid retryPlan delay {} to 1 for message id={}", val, taskMessage.id);
                    val = 1;
                }
                if (val > FuTaskBaseConfig.MAX_EXPIRE_SECONDS) {
                    log.warn("Normalizing excessive retryPlan delay {} to {} for message id={}", val, FuTaskBaseConfig.MAX_EXPIRE_SECONDS, taskMessage.id);
                    val = (int) FuTaskBaseConfig.MAX_EXPIRE_SECONDS;
                }
                normalized.add(val);
            }
            // Cap retry plan size to prevent unbounded retries from corrupted data
            if (normalized.size() > 20) {
                normalized = new ArrayList<>(normalized.subList(0, 20));
            }
            taskMessage.retryPlan = normalized;
        }
        if (taskMessage.executeExpire < 1) {
            taskMessage.executeExpire = 3600;
        }
        if (taskMessage.executeExpire > FuTaskBaseConfig.MAX_EXPIRE_SECONDS) {
            log.warn("Normalizing excessive executeExpire {} to {} for message id={}", taskMessage.executeExpire, FuTaskBaseConfig.MAX_EXPIRE_SECONDS, taskMessage.id);
            taskMessage.executeExpire = (int) FuTaskBaseConfig.MAX_EXPIRE_SECONDS;
        }
        if (taskMessage.delayTime < 0) {
            taskMessage.delayTime = 0;
        }
        if (taskMessage.delayTime > FuTaskBaseConfig.MAX_EXPIRE_SECONDS) {
            log.warn("Normalizing excessive delayTime {} to {} for message id={}", taskMessage.delayTime, FuTaskBaseConfig.MAX_EXPIRE_SECONDS, taskMessage.id);
            taskMessage.delayTime = (int) FuTaskBaseConfig.MAX_EXPIRE_SECONDS;
        }
        if (taskMessage.callerId != null && taskMessage.callerId.isEmpty()) {
            log.warn("Deserialized message has empty callerId, nullifying: id={}", taskMessage.id);
            taskMessage.callerId = null;
        }
        if (taskMessage.callerId != null && (taskMessage.callerId.contains(":") || taskMessage.callerId.contains("{") || taskMessage.callerId.contains("}"))) {
            log.warn("Deserialized message has unsafe callerId, nullifying: id={}", taskMessage.id);
            taskMessage.callerId = null;
        }
        if (taskMessage.callerId != null && taskMessage.callerId.length() > 256) {
            log.warn("Deserialized message has callerId exceeding 256 chars, nullifying: id={}", taskMessage.id);
            taskMessage.callerId = null;
        }
        // FUNCTION/CALLBACK mode without callerId cannot fulfill its contract — downgrade to NORMAL
        if (taskMessage.callerId == null && (FuTaskMode.FUNCTION.equals(taskMessage.mode) || FuTaskMode.CALLBACK.equals(taskMessage.mode))) {
            log.warn("Deserialized {} mode message has null callerId, downgrading to NORMAL: id={}", taskMessage.mode, taskMessage.id);
            taskMessage.mode = FuTaskMode.NORMAL;
        }
        if (taskMessage.status != null && !VALID_STATUSES.contains(taskMessage.status)) {
            taskMessage.status = FuTaskStatus.WAITING;
        }
        if (taskMessage.mode != null && !VALID_MODES.contains(taskMessage.mode)) {
            taskMessage.mode = FuTaskMode.NORMAL;
        }
        if (taskMessage.callbackStatus != null && !VALID_CALLBACK_STATUSES.contains(taskMessage.callbackStatus)) {
            taskMessage.callbackStatus = FuTaskStatus.WAITING;
        }
        if (taskMessage.callbackRetryTimes < 0) {
            taskMessage.callbackRetryTimes = 0;
        }
        if (taskMessage.resultExpire < 0) {
            taskMessage.resultExpire = 0;
        }
        if (taskMessage.resultExpire > FuTaskBaseConfig.MAX_EXPIRE_SECONDS) {
            log.warn("Normalizing excessive resultExpire {} to {} for message id={}", taskMessage.resultExpire, FuTaskBaseConfig.MAX_EXPIRE_SECONDS, taskMessage.id);
            taskMessage.resultExpire = (int) FuTaskBaseConfig.MAX_EXPIRE_SECONDS;
        }
        if (taskMessage.ttlBuffer < 0) {
            taskMessage.ttlBuffer = 0;
        }
        if (taskMessage.ttlBuffer > FuTaskBaseConfig.MAX_EXPIRE_SECONDS) {
            log.warn("Normalizing excessive ttlBuffer {} to {} for message id={}", taskMessage.ttlBuffer, FuTaskBaseConfig.MAX_EXPIRE_SECONDS, taskMessage.id);
            taskMessage.ttlBuffer = (int) FuTaskBaseConfig.MAX_EXPIRE_SECONDS;
        }
        if (taskMessage.error != null && taskMessage.error.length() > MAX_ERROR_LENGTH) {
            taskMessage.error = taskMessage.error.substring(0, MAX_ERROR_LENGTH);
        }
        if (taskMessage.callbackError != null && taskMessage.callbackError.length() > MAX_ERROR_LENGTH) {
            taskMessage.callbackError = taskMessage.callbackError.substring(0, MAX_ERROR_LENGTH);
        }
        if (taskMessage.retryDelay < 0) {
            taskMessage.retryDelay = 0;
        }
        if (taskMessage.retryDelay > FuTaskBaseConfig.MAX_EXPIRE_SECONDS) {
            log.warn("Normalizing excessive retryDelay {} to {} for message id={}", taskMessage.retryDelay, FuTaskBaseConfig.MAX_EXPIRE_SECONDS, taskMessage.id);
            taskMessage.retryDelay = (int) FuTaskBaseConfig.MAX_EXPIRE_SECONDS;
        }
        if (taskMessage.scheduleTime < 0) {
            taskMessage.scheduleTime = 0;
        }
        if (taskMessage.tag != null && taskMessage.tag.length() > MAX_TAG_LENGTH) {
            taskMessage.tag = taskMessage.tag.substring(0, MAX_TAG_LENGTH);
        }
        if (taskMessage.strategy != null && taskMessage.strategy.length() > MAX_STRATEGY_LENGTH) {
            taskMessage.strategy = taskMessage.strategy.substring(0, MAX_STRATEGY_LENGTH);
        }
        if (taskMessage.retryTimes < 0) {
            taskMessage.retryTimes = 0;
        }
        } catch (Exception e) {
            log.error("Error normalizing deserialized message fields for id={}, falling back to safe defaults: {}", taskMessage.id, e.getMessage());
            if (taskMessage.retryPlan == null) taskMessage.retryPlan = new ArrayList<>();
            if (taskMessage.executeExpire < 1) taskMessage.executeExpire = 3600;
            if (taskMessage.delayTime < 0) taskMessage.delayTime = 0;
        }
        if (taskMessage.getId() == null || taskMessage.getId().isEmpty()) {
            log.warn("Deserialized message has null/empty id, discarding: {}", item);
            return null;
        }
        if (taskMessage.id.contains(":") || taskMessage.id.contains("{") || taskMessage.id.contains("}")) {
            log.warn("Deserialized message has unsafe id (Redis key chars), discarding: id={}", taskMessage.id);
            return null;
        }
        if (taskMessage.id.length() > 256) {
            log.warn("Deserialized message has id exceeding 256 chars, discarding: id length={}", taskMessage.id.length());
            return null;
        }
        if (taskMessage.topic == null || taskMessage.topic.isEmpty()) {
            log.warn("Deserialized message has null/empty topic, discarding: {}", item);
            return null;
        }
        return taskMessage;
    }


    public static final List<String> requestFields = Collections.unmodifiableList(getFieldNames("request"));
    public static final List<String> retryFields = Collections.unmodifiableList(getFieldNames("retry"));
    public static final List<String> completeFields = Collections.unmodifiableList(getFieldNames("complete"));
    public static final List<String> callbackFields = Collections.unmodifiableList(getFieldNames("callback"));
    public static final List<String> allFields = Collections.unmodifiableList(Arrays.stream(FuTaskMessage.class.getDeclaredFields())
            .filter(f -> !Modifier.isStatic(f.getModifiers()))
            .map(Field::getName).collect(Collectors.toList()));

    private static final Set<String> requestFieldSet = Set.copyOf(requestFields);
    private static final Set<String> retryFieldSet = Set.copyOf(retryFields);
    private static final Set<String> completeFieldSet = Set.copyOf(completeFields);
    private static final Set<String> callbackFieldSet = Set.copyOf(callbackFields);

    private static List<String> getFieldNames(String tag) {
        List<String> strings = new ArrayList<>();
        for (Field field : FuTaskMessage.class.getDeclaredFields()) {
            FuTag fuTag = field.getAnnotation(FuTag.class);
            if (fuTag != null && Arrays.asList(fuTag.value()).contains(tag)) {
                strings.add(field.getName());
            }
        }
        return strings;
    }



}
