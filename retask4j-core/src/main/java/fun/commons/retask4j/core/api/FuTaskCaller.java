package fun.commons.retask4j.core.api;

import com.alibaba.fastjson2.JSONObject;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingDeque;
import org.redisson.api.RedissonClient;

import fun.commons.retask4j.core.config.FuTaskCallConfig;
import fun.commons.retask4j.core.exception.FuTaskCallbackException;
import fun.commons.retask4j.core.internal.FuTaskBase;
import fun.commons.retask4j.core.internal.FuTaskBatchManager;
import fun.commons.retask4j.core.internal.ThreadPoolHelper;
import fun.commons.retask4j.core.message.FuTaskMessage;
import fun.commons.retask4j.core.message.FuTaskMode;
import fun.commons.retask4j.core.message.FuTaskStatus;
import fun.commons.retask4j.core.monitor.FuTaskMonitor;
import fun.commons.retask4j.core.util.TtlUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
public class FuTaskCaller<R> extends FuTaskBase implements AutoCloseable {

    private final Cache<String, CompletableFuture<R>> returnMap;
    private final ThreadPoolExecutor subscribeFuncThreadPool;
    private final ThreadPoolExecutor competeFuncThreadPool;

    private final ThreadPoolExecutor subscribeCallbackThreadPool;
    private final ThreadPoolExecutor competeCallbackThreadPool;

    // Reset callback retry
    private final ThreadPoolExecutor resetCallbackRetryThreadPool;
    private final ScheduledExecutorService timeoutScheduler;
    private final FuTaskBatchManager<FuTaskMessage,Integer> batchSendManager;

    private final String callerId;
    private final  FuTaskCallConfig<R> config;
    private final Consumer<FuTaskMessage> callback;
    private static final Consumer<FuTaskMessage> defaultCallback = (msg) -> {};

    private final java.util.concurrent.atomic.AtomicBoolean running = new java.util.concurrent.atomic.AtomicBoolean(true);
    private final java.util.concurrent.atomic.AtomicBoolean started = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean shutdownCalled = new java.util.concurrent.atomic.AtomicBoolean(false);

    private final RBlockingDeque<String> returnDeque;

    @lombok.Getter
    private final FuTaskMonitor.CallerMonitor monitor = new FuTaskMonitor.CallerMonitor();


    public FuTaskCaller(RedissonClient redissonClient, FuTaskCallConfig<R> config) {
       this(redissonClient, config, defaultCallback);
    }

    public FuTaskCaller(RedissonClient redissonClient, FuTaskCallConfig<R> config, Consumer<FuTaskMessage> callback) {
        super(redissonClient, config);
        callerId = UUID.randomUUID().toString().replace("-", "");
        this.config = config;

        // Cache TTL must cover: maxDelayTime + execution + retry lifecycle
        // If maxRetryTtlBuffer is set (for per-task retryPlan overrides), use it;
        // otherwise compute from config's retryPlan
        long ttlBuffer = config.getMaxRetryTtlBuffer() > 0
                ? config.getMaxRetryTtlBuffer()
                : TtlUtils.computeTtlBuffer(config.getRetryPlan(), config.getExecuteExpire());
        String prefix = "caller-" + config.getTopic();
        competeFuncThreadPool = ThreadPoolHelper.createDaemonPool(prefix + "-compete-func", 8, 10000);
        competeCallbackThreadPool = ThreadPoolHelper.createDaemonPool(prefix + "-compete-callback", config.getCallbackMaxThreads(), 10000);
        subscribeFuncThreadPool = ThreadPoolHelper.createPollDispatchPool(prefix + "-subscribe-func", 2, 100);
        subscribeCallbackThreadPool = ThreadPoolHelper.createPollDispatchPool(prefix + "-subscribe-callback", 2, 100);
        resetCallbackRetryThreadPool = ThreadPoolHelper.createPollDispatchPool(prefix + "-callback-retry", 2, 100);
        timeoutScheduler = ThreadPoolHelper.createDaemonScheduledExecutor(prefix + "-timeout");

        long cacheTtlLong = (long) config.getMaxDelayTime() + (long) config.getExecuteExpire() * 2 + ttlBuffer;
        int cacheTtlSeconds = cacheTtlLong > 2_592_000 ? 2_592_000 : (int) Math.min(cacheTtlLong, Integer.MAX_VALUE);
        returnMap = CacheBuilder.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(cacheTtlSeconds))
                .maximumSize(config.getMaxFuncCacheSize())
                .removalListener(notification -> {
                    if (notification.wasEvicted()) {
                        monitor.cacheEviction.incrementAndGet();
                        @SuppressWarnings("unchecked")
                        CompletableFuture<R> f = (CompletableFuture<R>) notification.getValue();
                        if (f != null && !f.isDone()) {
                            String key = String.valueOf(notification.getKey());
                            log.warn("In-flight future evicted from cache (cause={}), task may complete but result will be lost: {}",
                                    notification.getCause(), key);
                            RuntimeException ex = new RuntimeException("Future evicted from cache: " + key);
                            try {
                                CompletableFuture.runAsync(() -> f.completeExceptionally(ex), competeFuncThreadPool);
                            } catch (Exception e) {
                                // Pool may be shut down; complete inline
                                f.completeExceptionally(ex);
                            }
                        }
                    }
                })
                .build();

        this.callback = callback;

        this.returnDeque = redissonClient.getBlockingDeque(funcPrefix + callerId, getCodec());
        // Set initial TTL on return deque so it's cleaned up if caller crashes without graceful shutdown.
        // complete_batch.lua refreshes this TTL on each result push.
        try {
            returnDeque.expire(Duration.ofHours(24));
        } catch (Exception e) {
            log.debug("Failed to set initial TTL on return deque", e);
        }

        this.batchSendManager = new FuTaskBatchManager<>(1000, 20, 4, prefix + "-send", (messages)-> this.send(messages));
    }

    public void start() {
        if (!started.compareAndSet(false, true)) return;
        for (int i = 0; i < subscribeFuncThreadPool.getMaximumPoolSize(); i++) {
            subscribeFuncThreadPool.execute(() -> {
                this.competeFuncBlock(1000);
            });
        }
        for (int i = 0; i < subscribeCallbackThreadPool.getMaximumPoolSize(); i++) {
            subscribeCallbackThreadPool.execute(() -> {
                this.competeCallbackBlock(1000);
            });
        }
        for (int i = 0; i < resetCallbackRetryThreadPool.getMaximumPoolSize(); i++) {
            resetCallbackRetryThreadPool.execute(() -> {
                this.runResetCallbackRetry(1000);
            });
        }
    }

    private void runResetCallbackRetry(int maxCount) {
        long sleepTimeMs = 0;
        int consecutiveFailures = 0;
        while (running.get()) {
            try {
                int count = doRemoveStoreSetToListScript(callbackPendingSet.getName(), callbackDeque.getName(), maxCount, false);
                if (count > 0) {
                    consecutiveFailures = 0;
                    sleepTimeMs = maxCount == count ? 0 : 50;
                    log.debug("pending poll count:{}", count);
                } else {
                    sleepTimeMs = sleepTimeMs == 0 ? 100 : Math.min(sleepTimeMs * 2, 1000);
                }
                if (sleep(sleepTimeMs)) break;
            } catch (Exception e) {
                consecutiveFailures++;
                log.error("runResetCallbackRetry error (consecutive failures: {})", consecutiveFailures, e);
                long backoff = consecutiveFailures >= 5 ? Math.min(30_000L, 1000L * (1L << Math.min(consecutiveFailures - 5, 5))) : 1000L;
                if (sleep(backoff)) break;
            } catch (Error e) {
                log.error("Fatal error in runResetCallbackRetry, attempting to continue", e);
                if (sleep(1000)) break;
            }
        }
    }

    private void competeCallbackBlock(int maxCount) {
        long sleepTimeMs = 0;
        int consecutiveFailures = 0;
        while (running.get()) {
            try {
                List<FuTaskMessage> fuTaskMessages = getMessagesForCallback(maxCount,getConfig().getCallbackPendingTimeout());
                int count = fuTaskMessages.size();
                if (count > 0) {
                    consecutiveFailures = 0;
                    for (FuTaskMessage msg : fuTaskMessages) {
                        competeCallbackThreadPool.execute(() -> completeCallback(msg));
                    }
                    sleepTimeMs = maxCount == count ? 0 : 50;
                    log.debug("callback poll count:{}", count);
                } else {
                    sleepTimeMs = sleepTimeMs == 0 ? 100 : Math.min(sleepTimeMs * 2, 1000);
                }
                if (sleep(sleepTimeMs)) break;
            } catch (Exception e) {
                consecutiveFailures++;
                log.error("competeCallbackBlock error (consecutive failures: {})", consecutiveFailures, e);
                long backoff = consecutiveFailures >= 5 ? Math.min(30_000L, 1000L * (1L << Math.min(consecutiveFailures - 5, 5))) : 1000L;
                if (sleep(backoff)) break;
            } catch (Error e) {
                log.error("Fatal error in competeCallbackBlock, attempting to continue", e);
                if (sleep(1000)) break;
            }
        }
    }


    private void competeFuncBlock(int maxCount) {
        long sleepTimeMs = 0;
        int consecutiveFailures = 0;
        while (running.get()) {
            try {
                List<String> ids = pollReturnMessageIds(returnDeque, maxCount);
                int count = ids.size();
                if (count > 0) {
                    consecutiveFailures = 0;
                    List<String> idsBatch = List.copyOf(ids);
                    competeFuncThreadPool.execute(() -> {
                        try {
                            completeFuncFutureById(idsBatch);
                        } catch (Error e) {
                            log.error("Fatal error completing futures for batch of {} ids", idsBatch.size(), e);
                        } catch (Exception e) {
                            log.error("Error completing futures for batch of {} ids", idsBatch.size(), e);
                        }
                    });
                    sleepTimeMs = maxCount == count ? 0 : 50;
                    log.debug("return poll count:{}", count);
                } else {
                    sleepTimeMs = sleepTimeMs == 0 ? 100 : Math.min(sleepTimeMs * 2, 1000);
                }
                if (sleep(sleepTimeMs)) break;
            } catch (Exception e) {
                consecutiveFailures++;
                log.error("competeFuncBlock error (consecutive failures: {})", consecutiveFailures, e);
                // Circuit breaker: after 5 consecutive failures, enter longer backoff to avoid
                // tight-looping against an unreachable Redis
                long backoff = consecutiveFailures >= 5 ? Math.min(30_000L, 1000L * (1L << Math.min(consecutiveFailures - 5, 5))) : 1000L;
                if (sleep(backoff)) break;
            } catch (Error e) {
                log.error("Fatal error in competeFuncBlock, attempting to continue", e);
                if (sleep(1000)) break;
            }
        }
    }

    public FuTaskMessage newTaskMessage(String id, JSONObject body) {
        Objects.requireNonNull(id, "Task ID must not be null");
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Task ID must not be empty");
        }
        if (id.length() > 256) {
            throw new IllegalArgumentException("Task ID must not exceed 256 characters: " + id.length());
        }
        if (id.contains(":") || id.contains("{") || id.contains("}")) {
            throw new IllegalArgumentException("Task ID contains unsafe characters for Redis keys: " + id);
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c < ' ' || c == 127) {
                throw new IllegalArgumentException("Task ID must not contain control characters: " + id);
            }
        }
        FuTaskMessage taskMessage = new FuTaskMessage(this.topic, id);
        taskMessage.setInput(body);
        taskMessage.setCallerId(callerId);
        taskMessage.setMode(FuTaskMode.NORMAL);
        taskMessage.setResultExpire(config.getResultExpire());
        taskMessage.setExecuteExpire(config.getExecuteExpire());
        List<Integer> retryPlan = new ArrayList<>(config.getRetryPlan());
        taskMessage.setRetryPlan(retryPlan);
        taskMessage.setStrategy(config.getStrategy());
        int ttlBuffer = TtlUtils.computeTtlBuffer(retryPlan, config.getExecuteExpire());
        taskMessage.setTtlBuffer(ttlBuffer);
        return taskMessage;
    }

    public FuTaskMessage newTaskMessage(JSONObject body) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        return  newTaskMessage(taskId, body);
    }

    public int sendTaskMessage(FuTaskMessage taskMessage) {
        return sendTaskMessage(List.of(taskMessage));
    }

    public int sendMessageBatch(FuTaskMessage taskMessage)  {
        try {
            return batchSendManager.submit(taskMessage).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            log.warn("Batch send timed out for task: {} -- task may still be enqueued", taskMessage.getId());
            throw new RuntimeException("Batch send timeout", e);
        }
    }

    /** Sends a task message in NORMAL mode via batch manager.
     *  NOTE: This method mutates the mode and resultExpire fields of the passed-in message. */
    public int sendTaskMessageBatch(FuTaskMessage taskMessage)   {
        taskMessage.setMode(FuTaskMode.NORMAL);
        return sendMessageBatch(taskMessage);
    }

    /** Sends a task message in FUNCTION mode via batch manager.
     *  NOTE: This method mutates the mode and resultExpire fields of the passed-in message. */
    public int sendFuncMessageBatch(FuTaskMessage taskMessage)   {
        if (taskMessage.getCallerId() == null) {
            throw new IllegalArgumentException("FUNCTION mode requires a non-null callerId on task: " + taskMessage.getId());
        }
        if (!callerId.equals(taskMessage.getCallerId())) {
            throw new IllegalArgumentException("FUNCTION mode callerId must match this FuTaskCaller instance. Expected: " + callerId + ", got: " + taskMessage.getCallerId());
        }
        taskMessage.setMode(FuTaskMode.FUNCTION);
        if (taskMessage.getResultExpire() < 60) {
            taskMessage.setResultExpire(60);
        }
        return sendMessageBatch(taskMessage);
    }
    /** Sends a task message in CALLBACK mode via batch manager.
     *  NOTE: This method mutates the mode and resultExpire fields of the passed-in message. */
    public int sendCallbackMessageBatch(FuTaskMessage taskMessage)   {
        if (taskMessage.getCallerId() == null) {
            throw new IllegalArgumentException("CALLBACK mode requires a non-null callerId on task: " + taskMessage.getId());
        }
        if (!callerId.equals(taskMessage.getCallerId())) {
            throw new IllegalArgumentException("CALLBACK mode callerId must match this FuTaskCaller instance. Expected: " + callerId + ", got: " + taskMessage.getCallerId());
        }
        taskMessage.setMode(FuTaskMode.CALLBACK);
        if (taskMessage.getResultExpire() < 60) {
            taskMessage.setResultExpire(60);
        }
        return sendMessageBatch(taskMessage);
    }


    /** Sends task messages in NORMAL mode. NOTE: mutates mode field of each message. */
    public int sendTaskMessage(List<FuTaskMessage> taskMessages) {
        for (FuTaskMessage taskMessage : taskMessages) {
            taskMessage.setMode(FuTaskMode.NORMAL);
        }
        if (!checkQueueDepth()) return 0;
        int result = super.send(taskMessages);
        if (result > 0) { monitor.sendSuccess.incrementAndGet(); } else { monitor.sendFail.incrementAndGet(); }
        return result;
    }

    public int sendFuncMessage(FuTaskMessage taskMessage) {
        return sendFuncMessage(List.of(taskMessage));
    }

    /** Sends task messages in FUNCTION mode. NOTE: mutates mode and resultExpire fields of each message. */
    public int sendFuncMessage(List<FuTaskMessage> taskMessages) {
        if (!started.get()) {
            log.warn("sendFuncMessage called before start() — FUNCTION-mode futures will not be completed until start() is called");
        }
        if (!checkQueueDepth()) return 0;
        for (FuTaskMessage taskMessage : taskMessages) {
            if (taskMessage.getCallerId() == null) {
                throw new IllegalArgumentException("FUNCTION mode requires a non-null callerId on task: " + taskMessage.getId());
            }
            if (!callerId.equals(taskMessage.getCallerId())) {
                throw new IllegalArgumentException("FUNCTION mode callerId must match this FuTaskCaller instance on task: " + taskMessage.getId());
            }
        }
        for (FuTaskMessage taskMessage : taskMessages) {
            taskMessage.setMode(FuTaskMode.FUNCTION);
            if (taskMessage.getResultExpire() < 60) {
                taskMessage.setResultExpire(60);
            }
        }
        int result = super.send(taskMessages);
        if (result > 0) { monitor.sendSuccess.incrementAndGet(); } else { monitor.sendFail.incrementAndGet(); }
        return result;
    }


    public int sendCallbackMessage(FuTaskMessage taskMessage) {
        return sendCallbackMessage(List.of(taskMessage));
    }

    /** Sends task messages in CALLBACK mode. NOTE: mutates mode and resultExpire fields of each message. */
    public int sendCallbackMessage(List<FuTaskMessage> fuTaskMessages) {
        if (!started.get()) {
            log.warn("sendCallbackMessage called before start() — callback messages will not be processed until start() is called");
        }
        if (!checkQueueDepth()) return 0;
        for (FuTaskMessage taskMessage : fuTaskMessages) {
            if (taskMessage.getCallerId() == null) {
                throw new IllegalArgumentException("CALLBACK mode requires a non-null callerId on task: " + taskMessage.getId());
            }
            if (!callerId.equals(taskMessage.getCallerId())) {
                throw new IllegalArgumentException("CALLBACK mode callerId must match this FuTaskCaller instance on task: " + taskMessage.getId());
            }
        }
        for (FuTaskMessage taskMessage : fuTaskMessages) {
            taskMessage.setMode(FuTaskMode.CALLBACK);
            if (taskMessage.getResultExpire() < 60) {
                taskMessage.setResultExpire(60);
            }
        }
        int result = super.send(fuTaskMessages);
        if (result > 0) { monitor.sendSuccess.incrementAndGet(); } else { monitor.sendFail.incrementAndGet(); }
        return result;
    }


    public int funcAsync(FuTaskMessage fuMessage, BiConsumer<R, ? super Throwable> onComplete) {
        String id = fuMessage.getId();
        Objects.requireNonNull(id, "FuTaskMessage ID must not be null for FUNCTION mode");
        CompletableFuture<R> future = new CompletableFuture<>();
        registerCallback(future, onComplete);
        putFuture(id, future);
        try {
            int result = sendFuncMessage(fuMessage);
            if (result <= 0) {
                returnMap.asMap().remove(id, future);
                future.completeExceptionally(new RuntimeException("Failed to send task message"));
            } else {
                scheduleTimeout(future, computeTaskTimeoutSeconds(fuMessage));
            }
            return result;
        } catch (Exception e) {
            returnMap.asMap().remove(id, future);
            future.completeExceptionally(e);
            return 0;
        }
    }

    public CompletableFuture<R> funcAsyncBatch(FuTaskMessage fuMessage, BiConsumer<R, ? super Throwable> onComplete) {
        String id = fuMessage.getId();
        Objects.requireNonNull(id, "FuTaskMessage ID must not be null for FUNCTION mode");
        CompletableFuture<R> future = new CompletableFuture<>();
        registerCallback(future, onComplete);
        putFuture(id, future);
        // Use async batch send to avoid the race between blocking .get() timeout and actual send
        sendFuncMessageAsyncBatch(fuMessage, future);
        return future;
    }

    public CompletableFuture<R> funcAsyncBatch(FuTaskMessage fuMessage) {
        String id = fuMessage.getId();
        Objects.requireNonNull(id, "FuTaskMessage ID must not be null for FUNCTION mode");
        CompletableFuture<R> future = new CompletableFuture<>();
        putFuture(id, future);
        sendFuncMessageAsyncBatch(fuMessage, future);
        return future;
    }

    private void sendFuncMessageAsyncBatch(FuTaskMessage fuMessage, CompletableFuture<R> future) {
        if (fuMessage.getCallerId() == null) {
            returnMap.asMap().remove(fuMessage.getId(), future);
            future.completeExceptionally(new IllegalArgumentException("FUNCTION mode requires a non-null callerId on task: " + fuMessage.getId()));
            return;
        }
        if (!callerId.equals(fuMessage.getCallerId())) {
            returnMap.asMap().remove(fuMessage.getId(), future);
            future.completeExceptionally(new IllegalArgumentException("FUNCTION mode callerId must match this FuTaskCaller instance on task: " + fuMessage.getId()));
            return;
        }
        fuMessage.setMode(FuTaskMode.FUNCTION);
        if (fuMessage.getResultExpire() < 60) {
            fuMessage.setResultExpire(60);
        }
        String id = fuMessage.getId();
        if (!checkQueueDepth()) {
            returnMap.asMap().remove(id, future);
            future.completeExceptionally(new RuntimeException("Work queue depth exceeds maxQueueDepth"));
            return;
        }
        batchSendManager.submit(fuMessage).whenComplete((result, ex) -> {
            if (ex != null) {
                log.warn("Async batch send failed for task: {}", id, ex);
                returnMap.asMap().remove(id, future);
                future.completeExceptionally(ex instanceof CompletionException ? ex.getCause() : ex);
            } else if (result == null || result <= 0) {
                log.warn("Async batch send returned 0 for task: {}", id);
                returnMap.asMap().remove(id, future);
                future.completeExceptionally(new RuntimeException("Failed to send task message"));
            } else {
                monitor.sendSuccess.incrementAndGet();
                // Schedule timeout only after successful send to avoid premature timeouts
                // during batch manager backpressure
                try {
                    scheduleTimeout(future, computeTaskTimeoutSeconds(fuMessage));
                } catch (Exception e) {
                    log.warn("Failed to schedule timeout for task: {}", id, e);
                }
            }
        });
    }


    public CompletableFuture<R> funcAsync(FuTaskMessage fuMessage) {
        String id = fuMessage.getId();
        Objects.requireNonNull(id, "FuTaskMessage ID must not be null for FUNCTION mode");
        CompletableFuture<R> future = new CompletableFuture<>();
        putFuture(id, future);
        try {
            int result = sendFuncMessage(fuMessage);
            if (result <= 0) {
                returnMap.asMap().remove(id, future);
                future.completeExceptionally(new RuntimeException("Failed to send task message"));
            } else {
                scheduleTimeout(future, computeTaskTimeoutSeconds(fuMessage));
            }
        } catch (Exception e) {
            returnMap.asMap().remove(id, future);
            future.completeExceptionally(e);
        }
        return future;
    }


    public void funcAsync(FuTaskMessage fuMessage, CompletableFuture<R> future) {
        funcAsync(Map.entry(fuMessage, future));
    }

    public void funcAsync(Map.Entry<FuTaskMessage, CompletableFuture<R>> entry) {
        funcAsync(List.of(entry));
    }

    public void funcAsync(List<Map.Entry<FuTaskMessage, CompletableFuture<R>>> entryList) {
        List<FuTaskMessage> taskMessages = new ArrayList<>();
        List<CompletableFuture<R>> futures = new ArrayList<>();
        for (Map.Entry<FuTaskMessage, CompletableFuture<R>> entry : entryList) {
            FuTaskMessage fuMessage = entry.getKey();
            String id = fuMessage.getId();
            if (id == null) {
                entry.getValue().completeExceptionally(new NullPointerException("FuTaskMessage ID must not be null for FUNCTION mode"));
                continue;
            }
            CompletableFuture<R> future = entry.getValue();
            taskMessages.add(fuMessage);
            putFuture(id, future);
            futures.add(future);
        }
        if (taskMessages.isEmpty()) return;
        try {
            int result = sendFuncMessage(taskMessages);
            if (result == 0) {
                // Complete send failure: no worker will process these tasks
                for (int i = 0; i < futures.size(); i++) {
                    returnMap.asMap().remove(taskMessages.get(i).getId(), futures.get(i));
                    futures.get(i).completeExceptionally(new RuntimeException("Failed to send task messages"));
                }
            } else if (result == taskMessages.size()) {
                for (int i = 0; i < futures.size(); i++) {
                    scheduleTimeout(futures.get(i), computeTaskTimeoutSeconds(taskMessages.get(i)));
                }
            } else {
                log.warn("Batch send returned {} but expected {} for topic {}", result, taskMessages.size(), topic);
                for (int i = 0; i < futures.size(); i++) {
                    scheduleTimeout(futures.get(i), computeTaskTimeoutSeconds(taskMessages.get(i)));
                }
            }
        } catch (Exception e) {
            for (int i = 0; i < futures.size(); i++) {
                returnMap.asMap().remove(taskMessages.get(i).getId(), futures.get(i));
                futures.get(i).completeExceptionally(e);
            }
        }
    }

    public void funcAsyncComplete(List<Map.Entry<FuTaskMessage,  BiConsumer<R, ? super Throwable>>> entryList) {
        List<FuTaskMessage> taskMessages = new ArrayList<>();
        List<CompletableFuture<R>> futures = new ArrayList<>();
        for (Map.Entry<FuTaskMessage,  BiConsumer<R, ? super Throwable>> entry : entryList) {
            FuTaskMessage fuMessage = entry.getKey();
            String id = fuMessage.getId();
            if (id == null) {
                CompletableFuture<R> failed = new CompletableFuture<>();
                failed.completeExceptionally(new NullPointerException("FuTaskMessage ID must not be null for FUNCTION mode"));
                registerCallback(failed, entry.getValue());
                continue;
            }
            CompletableFuture<R> future = new CompletableFuture<>();
            registerCallback(future, entry.getValue());
            taskMessages.add(fuMessage);
            putFuture(id, future);
            futures.add(future);
        }
        if (taskMessages.isEmpty()) return;
        try {
            int result = sendFuncMessage(taskMessages);
            if (result == 0) {
                for (int i = 0; i < futures.size(); i++) {
                    returnMap.asMap().remove(taskMessages.get(i).getId(), futures.get(i));
                    futures.get(i).completeExceptionally(new RuntimeException("Failed to send task messages"));
                }
            } else if (result == taskMessages.size()) {
                for (int i = 0; i < futures.size(); i++) {
                    scheduleTimeout(futures.get(i), computeTaskTimeoutSeconds(taskMessages.get(i)));
                }
            } else {
                log.warn("Batch send returned {} but expected {} for topic {}", result, taskMessages.size(), topic);
                for (int i = 0; i < futures.size(); i++) {
                    scheduleTimeout(futures.get(i), computeTaskTimeoutSeconds(taskMessages.get(i)));
                }
            }
        } catch (Exception e) {
            for (int i = 0; i < futures.size(); i++) {
                returnMap.asMap().remove(taskMessages.get(i).getId(), futures.get(i));
                futures.get(i).completeExceptionally(e);
            }
        }
    }



    public void completeFuncFutureById(String messageId) {
        completeFuncFutureById(List.of(messageId));
    }

    public void completeFuncFutureById(List<String> messageIds) {
        List<FuTaskMessage> fuMessages;
        try {
            fuMessages = getMessagesById(messageIds);
        } catch (Exception e) {
            // Transient Redis error — do NOT fail futures; IDs remain consumed from the deque,
            // but the result hashes may still be in Redis. Log and return to avoid irrecoverable data loss.
            log.error("Failed to retrieve result messages from Redis for {} IDs, skipping future completion to avoid spurious failures", messageIds.size(), e);
            return;
        }
        Set<String> foundIds = fuMessages.stream().map(FuTaskMessage::getId).collect(Collectors.toSet());
        for (String id : messageIds) {
            if (!foundIds.contains(id)) {
                CompletableFuture<R> future = returnMap.getIfPresent(id);
                if (future != null) {
                    monitor.funcResultMissing.incrementAndGet();
                    future.completeExceptionally(new RuntimeException("Result expired before retrieval: " + id));
                    // Conditional remove: only evict if this future is still the cached value
                    returnMap.asMap().remove(id, future);
                }
            }
        }
        completeFuncFuture(fuMessages);
    }

    public void completeFuncFuture(List<FuTaskMessage> fuTaskMessages) {
        for (FuTaskMessage msg : fuTaskMessages) {
            String id = msg.getId();
            CompletableFuture<R> future = returnMap.getIfPresent(id);
            if (future != null) {
                monitor.funcComplete.incrementAndGet();
                try {
                    if (FuTaskStatus.SUCCESS.equals(msg.getStatus())) {
                        JSONObject object = msg.getOutput();
                        if (object == null) {
                            future.complete(null);
                        } else {
                            R result = object.to(getConfig().getReturnCls());
                            future.complete(result);
                        }
                    } else if (FuTaskStatus.FAIL.equals(msg.getStatus())) {
                        future.completeExceptionally(new RuntimeException(msg.getError()));
                    } else {
                        future.completeExceptionally(new RuntimeException("task status error"));
                    }
                } catch (Error e) {
                    log.error("Fatal error completing future for task: {}", id, e);
                    future.completeExceptionally(e);
                    if (e instanceof OutOfMemoryError) throw e;
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
                // Remove only if this future is still the cached value (prevents removing a re-registered future)
                returnMap.asMap().remove(id, future);
            } else {
                log.debug("Future not found for task: {}, may have expired", id);
            }
        }
    }

    private void putFuture(String id, CompletableFuture<R> future) {
        CompletableFuture<R> prev = returnMap.asMap().putIfAbsent(id, future);
        if (prev != null) {
            log.warn("Duplicate task ID detected: {}, completing old future exceptionally", id);
            prev.completeExceptionally(new RuntimeException("Future replaced by duplicate task ID: " + id));
            returnMap.put(id, future);
        }
    }

    private void scheduleTimeout(CompletableFuture<R> future) {
        scheduleTimeout(future, config.getRequestTimeout());
    }

    private void scheduleTimeout(CompletableFuture<R> future, long timeoutSeconds) {
        ScheduledFuture<?> timeoutTask = timeoutScheduler.schedule(() -> {
            if (!future.isDone()) {
                monitor.funcTimeout.incrementAndGet();
                future.completeExceptionally(new java.util.concurrent.TimeoutException("Function call timed out after " + timeoutSeconds + "s"));
            }
        }, timeoutSeconds, TimeUnit.SECONDS);
        future.whenComplete((r, ex) -> timeoutTask.cancel(false));
    }

    private long computeTaskTimeoutSeconds(FuTaskMessage msg) {
        // The timeout must cover the task's full lifecycle:
        // delayTime + (initial execution) + (retry delays + execution windows)
        // Use the greater of requestTimeout and the computed task lifetime
        long taskLifetime = (long) msg.getDelayTime() + msg.getExecuteExpire() + msg.getTtlBuffer();
        // Add a 60s buffer for queue wait and result delivery
        long timeout = taskLifetime + 60;
        return Math.max(timeout, config.getRequestTimeout());
    }

    private CompletableFuture<R> registerCallback(CompletableFuture<R> future, BiConsumer<R, ? super Throwable> onComplete) {
        try {
            future.whenCompleteAsync(onComplete, competeFuncThreadPool);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // Pool may be shut down; fall back to inline execution
            future.whenComplete(onComplete);
        }
        return future;
    }

    public void completeCallbackById(String messageId) {
        List<FuTaskMessage> messages;
        try {
            messages = getMessagesById(List.of(messageId));
        } catch (Exception e) {
            // Transient Redis error — do NOT treat as expired; log and skip to avoid losing the callback
            log.error("Failed to retrieve callback message from Redis for id {}, skipping to avoid spurious expiry", messageId, e);
            return;
        }
        if (messages.isEmpty()) {
            log.warn("Callback message hash expired before retrieval, removing from pending to break re-delivery cycle: {}", messageId);
            try {
                callbackPendingSet.remove(messageId);
            } catch (Exception e) {
                log.error("Failed to remove expired callback from pending set: {}", messageId, e);
            }
            return;
        }
        completeCallback(messages);
    }


    public void completeCallback(List<FuTaskMessage> fuTaskMessages) {
        for (FuTaskMessage fuTaskMessage : fuTaskMessages) {
            try {
                competeCallbackThreadPool.execute(() -> completeCallback(fuTaskMessage));
            } catch (java.util.concurrent.RejectedExecutionException e) {
                log.warn("Callback thread pool rejected execution for task: {}, executing inline", fuTaskMessage.getId());
                try {
                    completeCallback(fuTaskMessage);
                } catch (Exception ex) {
                    log.error("Inline callback execution failed for task: {}", fuTaskMessage.getId(), ex);
                }
            }
        }
    }

    private void completeCallback(FuTaskMessage fuTaskMessage) {
        if (FuTaskStatus.SUCCESS.equals(fuTaskMessage.getCallbackStatus())) {
            return;
        }
        try {
            callback.accept(fuTaskMessage);
            fuTaskMessage.setCallbackStatus(FuTaskStatus.SUCCESS);
            monitor.callbackComplete.incrementAndGet();
            setCallbackMessageSafe(fuTaskMessage);
        }catch (Exception e){
            log.error("callback accept error",e);
            monitor.callbackFail.incrementAndGet();
            boolean retryable = !(e instanceof FuTaskCallbackException fce) || fce.isRetryable();
            if (retryable) {
                int callbackRetryTimes = fuTaskMessage.getCallbackRetryTimes();
                if (callbackRetryTimes < getConfig().getCallbackRetryTimes()) {
                    fuTaskMessage.setCallbackRetryTimes(callbackRetryTimes + 1);
                    fuTaskMessage.setCallbackStatus(FuTaskStatus.WAITING);
                    fuTaskMessage.setCallbackError(e.getMessage());
                }else{
                    fuTaskMessage.setCallbackStatus(FuTaskStatus.FAIL);
                    fuTaskMessage.setCallbackError(e.getMessage());
                }
            } else {
                fuTaskMessage.setCallbackStatus(FuTaskStatus.FAIL);
                fuTaskMessage.setCallbackError(e.getMessage());
            }
            setCallbackMessageSafe(fuTaskMessage);
        } catch (Error e) {
            log.error("Fatal error in callback for task: {}", fuTaskMessage.getId(), e);
            fuTaskMessage.setCallbackStatus(FuTaskStatus.FAIL);
            fuTaskMessage.setCallbackError(e.getMessage());
            try {
                setCallbackMessageSafe(fuTaskMessage);
            } catch (Exception cleanupEx) {
                log.error("Failed to set callback message after fatal error: {}", fuTaskMessage.getId(), cleanupEx);
            }
            throw e;
        }
    }

    private void setCallbackMessageSafe(FuTaskMessage fuTaskMessage) {
        try {
            setCallbackMessages(List.of(fuTaskMessage));
        } catch (Exception e) {
            log.error("setCallbackMessage error for task: {} -- callback may be re-delivered on next retry cycle", fuTaskMessage.getId(), e);
        }
    }

    private void setCallbackMessages(List<FuTaskMessage> fuTaskMessages) {
        if (fuTaskMessages.isEmpty()) return;
        doSetCallbackBatchScript(fuTaskMessages, getConfig().getCallbackRetryInterval());
    }

    private FuTaskCallConfig<R> getConfig() {
        return (FuTaskCallConfig<R>) config;
    }

    private boolean checkQueueDepth() {
        int maxQueueDepth = getConfig().getMaxQueueDepth();
        if (maxQueueDepth > 0) {
            try {
                int currentDepth = workingDeque.size();
                if (currentDepth >= maxQueueDepth) {
                    log.warn("Work queue depth {} exceeds maxQueueDepth {}, rejecting task submission", currentDepth, maxQueueDepth);
                    return false;
                }
            } catch (Exception e) {
                log.debug("Failed to check queue depth, allowing submission", e);
            }
        }
        return true;
    }

    @Override
    public void close() {
        shutdown();
    }

    public void shutdown() {
        if (!shutdownCalled.compareAndSet(false, true)) return;
        log.info("Shutting down FuTaskCaller for topic: {}", topic);
        running.set(false);
        // Flush batch manager first — it may submit to compete pools
        batchSendManager.shutdown();
        // Stop subscribe pools first so they don't submit to compete pools after those are shut down
        List<ThreadPoolExecutor> subscribePools = List.of(
                subscribeFuncThreadPool, subscribeCallbackThreadPool, resetCallbackRetryThreadPool);
        for (ThreadPoolExecutor pool : subscribePools) {
            pool.shutdownNow();
        }
        for (ThreadPoolExecutor pool : subscribePools) {
            try {
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Subscribe pool did not terminate in 5s: {}", pool);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // Drain remaining results from the return deque BEFORE shutting down compete pools,
        // so that result completions run on the proper thread pool instead of the shutdown thread
        try {
            List<String> remainingIds = new ArrayList<>();
            returnDeque.drainTo(remainingIds);
            if (!remainingIds.isEmpty()) {
                log.info("Drained {} remaining results from return deque on shutdown", remainingIds.size());
                completeFuncFutureById(remainingIds);
            }
        } catch (Exception e) {
            log.debug("Failed to drain return deque on shutdown", e);
        }
        try {
            returnDeque.delete();
        } catch (Exception e) {
            log.debug("Failed to delete return deque on shutdown", e);
        }
        // Drain remaining CALLBACK-mode results before shutting down compete pools
        try {
            List<String> remainingCallbackIds = new ArrayList<>();
            callbackDeque.drainTo(remainingCallbackIds);
            if (!remainingCallbackIds.isEmpty()) {
                log.info("Drained {} remaining callbacks from callback deque on shutdown", remainingCallbackIds.size());
                for (String id : remainingCallbackIds) {
                    completeCallbackById(id);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to drain callback deque on shutdown", e);
        }
        // Now stop compete pools (subscribe threads are dead and deque is drained)
        List<ThreadPoolExecutor> competePools = List.of(competeFuncThreadPool, competeCallbackThreadPool);
        for (ThreadPoolExecutor pool : competePools) {
            pool.shutdown();
        }
        for (ThreadPoolExecutor pool : competePools) {
            try {
                if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("Compete pool did not terminate in 10s, forcing: {}", pool);
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        timeoutScheduler.shutdownNow();
        try {
            if (!timeoutScheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                log.warn("Timeout scheduler did not terminate in 2s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        returnMap.asMap().forEach((id, future) -> {
            future.completeExceptionally(new RuntimeException("FuTaskCaller shut down"));
        });
    }

}
