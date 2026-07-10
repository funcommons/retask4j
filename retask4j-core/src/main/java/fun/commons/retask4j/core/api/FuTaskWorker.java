package fun.commons.retask4j.core.api;

import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RedissonClient;

import fun.commons.retask4j.core.config.FuTaskWorkConfig;
import fun.commons.retask4j.core.exception.FuTaskAssertionException;
import fun.commons.retask4j.core.exception.FuTaskExpiredException;
import fun.commons.retask4j.core.exception.FuTaskRetryExhaustedException;
import fun.commons.retask4j.core.internal.FuTaskBase;
import fun.commons.retask4j.core.internal.FuTaskBatchManager;
import fun.commons.retask4j.core.internal.ThreadPoolHelper;
import fun.commons.retask4j.core.message.FuTaskMessage;
import fun.commons.retask4j.core.message.FuTaskStatus;
import fun.commons.retask4j.core.monitor.FuTaskMonitor;
import fun.commons.retask4j.core.strategy.FuTaskWorkStrategy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


@Slf4j
public class FuTaskWorker extends FuTaskBase implements AutoCloseable {

    private final FuTaskExecutor taskExecutor;

    private final ThreadPoolExecutor workerThreadPool;

    // Consume listener thread pool
    private final ThreadPoolExecutor subscribeThreadPool;
    private final ThreadPoolExecutor resetPendingThreadPool;
    private final ThreadPoolExecutor resetTimingThreadPool;
    private final ThreadPoolExecutor resetRetryThreadPool;

    private final ScheduledExecutorService monitorScheduler;

    @Getter
    private final FuTaskMonitor.WorkerMonitor monitor = new FuTaskMonitor.WorkerMonitor();

    private final java.util.concurrent.atomic.AtomicBoolean running = new java.util.concurrent.atomic.AtomicBoolean(true);
    private final java.util.concurrent.atomic.AtomicBoolean started = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean shutdownCalled = new java.util.concurrent.atomic.AtomicBoolean(false);

    private final FuTaskBatchManager<FuTaskMessage,Void> batchRetryManager;
    private final FuTaskBatchManager<FuTaskMessage,Void> batchCompeteManager;

    private FuTaskWorkConfig getConfig(){
        return (FuTaskWorkConfig) config;
    }

    public FuTaskWorker(RedissonClient redissonClient, FuTaskWorkConfig config, FuTaskExecutor taskExecutor) {
        super(redissonClient, config);
        java.util.Objects.requireNonNull(taskExecutor, "taskExecutor must not be null");
        this.taskExecutor = taskExecutor;
        int maxThreads = config.getMaxConsumeThreads();
        String prefix = "worker-" + config.getTopic();

        this.workerThreadPool = ThreadPoolHelper.createDaemonPool(prefix, maxThreads, 10000);
        this.subscribeThreadPool = ThreadPoolHelper.createPollDispatchPool(prefix + "-subscribe", 1, 100);
        this.resetPendingThreadPool = ThreadPoolHelper.createPollDispatchPool(prefix + "-pending", 1, 100);
        this.resetTimingThreadPool = ThreadPoolHelper.createPollDispatchPool(prefix + "-timing", 1, 100);
        this.resetRetryThreadPool = ThreadPoolHelper.createPollDispatchPool(prefix + "-retry", 1, 100);

        this.monitorScheduler = ThreadPoolHelper.createDaemonScheduledExecutor(prefix + "-monitor");

        batchCompeteManager = new FuTaskBatchManager<>(1000, 50, 4, prefix + "-complete", messages -> {
            int count = this.complete(messages);
            if (count == 0 && !messages.isEmpty()) {
                log.warn("complete batch returned 0 for {} messages (hashes may have expired)", messages.size());
            }
            return null;
        });

        batchRetryManager = new FuTaskBatchManager<>(1000, 50, 4, prefix + "-retry", messages -> {
            int count = this.retry(messages);
            if (count == 0 && !messages.isEmpty()) {
                log.warn("retry batch returned 0 for {} messages (hashes may have expired)", messages.size());
            }
            return null;
        });

    }

    public void start() {
        if (!started.compareAndSet(false, true)) return;

        for (int i = 0; i < subscribeThreadPool.getMaximumPoolSize(); i++) {
            subscribeThreadPool.execute(() -> {
                this.subscribeBatchBlock();
            });
        }

        for (int i = 0; i < resetPendingThreadPool.getMaximumPoolSize(); i++) {
            resetPendingThreadPool.execute(() -> {
                this.runResetPending(1000);
            });
        }

        for (int i = 0; i < resetTimingThreadPool.getMaximumPoolSize(); i++) {
            resetTimingThreadPool.execute(() -> {
                this.runResetTiming(1000);
            });
        }


        for (int i = 0; i < resetRetryThreadPool.getMaximumPoolSize(); i++) {
            resetRetryThreadPool.execute(() -> {
                this.runResetRetry(1000);
            });
        }

        monitorScheduler.scheduleAtFixedRate(() -> {
            try {
                monitor.workerCompleted.set(workerThreadPool.getCompletedTaskCount());
                monitor.workerActiveCount.set(workerThreadPool.getActiveCount());
            } catch (Exception e) {
                log.warn("Monitor update failed", e);
            }
        }, 1000, 1000, java.util.concurrent.TimeUnit.MILLISECONDS);

    }

    private static final int MAX_POLL_COUNT = 200;

    private void subscribeBatchBlock() {
        long sleepTimeMs = 0;
        int maxThreads = getConfig().getMaxConsumeThreads();
        int consecutiveFailures = 0;

        while (running.get()) {
            try {
                int idleWorkingCount = Math.max(0, Math.min(maxThreads - workerThreadPool.getActiveCount(), MAX_POLL_COUNT));
                int usedWorkingCount = 0;
                if (idleWorkingCount > 0) {
                    usedWorkingCount = subscribe(idleWorkingCount);
                }

                if (idleWorkingCount > 0) {
                    if (usedWorkingCount > 0) {
                        consecutiveFailures = 0;
                        sleepTimeMs = 0;
                    } else {
                        // Exponential backoff: 50 → 100 → 200 → 400 → 800 → 1000 (cap)
                        sleepTimeMs = sleepTimeMs == 0 ? 50 : Math.min(sleepTimeMs * 2, 1000);
                    }
                } else {
                    sleepTimeMs = 100;
                }

                if (sleep(sleepTimeMs)) break;
            } catch (Exception e) {
                consecutiveFailures++;
                log.error("subscribeBatchBlock error (consecutive failures: {})", consecutiveFailures, e);
                long backoff = consecutiveFailures >= 5 ? Math.min(30_000L, 1000L * (1L << Math.min(consecutiveFailures - 5, 5))) : 1000L;
                if (sleep(backoff)) break;
            } catch (Error e) {
                log.error("Fatal error in subscribeBatchBlock, attempting to continue", e);
                if (sleep(1000)) break;
            }
        }
    }

    private int subscribe(int maxCount) {
        List<FuTaskMessage> taskList = List.of();
        int result = 0;
        try {
            taskList = getMessagesForWork( maxCount,  getConfig().getPendingTimeout());
            result = taskList.size();
        } catch (Exception e) {
            log.error("subscribe error", e);
        } catch (Error e) {
            log.error("Fatal error in subscribe, attempting to continue", e);
        }

        if (result > 0) {
            log.debug("subscribe count:{}", result);

            for (FuTaskMessage task : taskList) {
                workerThreadPool.execute(() -> consume(task));
            }
        }
        return result;
    }


    private void failAndComplete(FuTaskMessage taskMessage, long beginTime, String error, java.util.concurrent.atomic.AtomicBoolean handled) {
        taskMessage.setOutput(null);
        taskMessage.setExecuteTime(beginTime);
        taskMessage.setCompleteTime(System.currentTimeMillis());
        taskMessage.setStatus(FuTaskStatus.FAIL);
        taskMessage.setError(error);
        batchCompeteManager.submit(taskMessage, (result, ex) -> {
            if (ex != null) log.error("complete error for failed task: {}", taskMessage.getId(), ex);
        });
        handled.set(true);
    }

    private int consume(FuTaskMessage taskMessage){
        // Track whether doConsume already completed/retried the task.
        // doConsume submits to batchCompeteManager or batchRetryManager internally,
        // so we must not double-complete on an unexpected exception.
        java.util.concurrent.atomic.AtomicBoolean handled = new java.util.concurrent.atomic.AtomicBoolean(false);
        try {
            int result = doConsume(taskMessage, handled);
            return result;
        } catch (Exception e) {
            log.error("Error in consume for task: {}", taskMessage != null ? taskMessage.getId() : "null", e);
            if (handled.compareAndSet(false, true) && taskMessage != null && !FuTaskStatus.isTerminal(taskMessage.getStatus())) {
                taskMessage.setStatus(FuTaskStatus.FAIL);
                taskMessage.setError(e.getMessage());
                batchCompeteManager.submit(taskMessage, (result, ex) -> {
                    if (ex != null) log.error("complete error after exception for task: {}", taskMessage.getId(), ex);
                });
            }
            return -1;
        } catch (Error e) {
            // Error (OOM, StackOverflow) must still clean up the task to prevent it from
            // being stuck in PENDING state forever, then re-throw to allow thread death.
            log.error("Fatal error in consume for task: {}", taskMessage != null ? taskMessage.getId() : "null", e);
            if (handled.compareAndSet(false, true) && taskMessage != null && !FuTaskStatus.isTerminal(taskMessage.getStatus())) {
                try {
                    taskMessage.setStatus(FuTaskStatus.FAIL);
                    taskMessage.setError(e.getMessage());
                    batchCompeteManager.submit(taskMessage, (result, ex) -> {
                        if (ex != null) log.error("complete error after fatal error for task: {}", taskMessage.getId(), ex);
                    });
                } catch (Throwable cleanupError) {
                    log.error("Cleanup failed after fatal error for task: {}", taskMessage.getId(), cleanupError);
                    try { pendingSet.remove(taskMessage.getId()); } catch (Throwable t) { /* give up */ }
                }
            }
            // Signal shutdown for OOM (fatal to JVM), but let other Errors (e.g. StackOverflow) kill only this thread
            if (e instanceof OutOfMemoryError) {
                running.set(false);
            }
            throw e;
        }
    }

    private int doConsume(FuTaskMessage taskMessage, java.util.concurrent.atomic.AtomicBoolean handled){


        long beginTime = System.currentTimeMillis();
        FuTaskWorkStrategy strategy = taskMessage !=null ? getStrategy(taskMessage.getStrategy()) : null;

        Exception finallyError = null;
        String id = null;

        // Check expiration
        if (taskMessage == null) {
            finallyError = new FuTaskExpiredException("message not found expired"); monitor.expired.incrementAndGet();
        }
        // Check expiration 2: total lifetime = delayTime + ttlBuffer (which includes retry delays + execution windows)
        else if (taskMessage.getTtlBuffer() > 0
                ? beginTime > taskMessage.getCreateTime() + ((long) taskMessage.getDelayTime() * 1000) + ((long) taskMessage.getTtlBuffer() * 1000)
                : beginTime > taskMessage.getCreateTime() + ((long) taskMessage.getDelayTime() * 1000) + ((long) taskMessage.getExecuteExpire() * 1000)) {
            id = taskMessage.getId();
            finallyError = new FuTaskExpiredException("message time expired"); monitor.expired.incrementAndGet();
        }
        // Check if retries exhausted (only after at least one execution attempt; fresh tasks with empty retryPlan should execute once)
        else if (taskMessage.getRetryTimes() > 0 && (taskMessage.getRetryPlan().isEmpty() || taskMessage.getRetryTimes() >= taskMessage.getRetryPlan().size())) {
            id = taskMessage.getId();
            finallyError = new FuTaskRetryExhaustedException("retry times out"); monitor.retryExhausted.incrementAndGet();
        } else {
            id = taskMessage.getId();
        }

        if (finallyError != null) {

            // Execution failure event handler
            onFail( id, strategy ,finallyError);
            onFinallyFail( id, strategy , finallyError);
            onComplete( id, strategy,null, finallyError);

            if (taskMessage != null) {
                failAndComplete(taskMessage, beginTime, finallyError.getMessage(), handled);
            }

            return -1;

        }else {

            monitor.consume.incrementAndGet();

            // Skip re-execution if task is already in terminal state (re-delivered after pending timeout)
            if (FuTaskStatus.isTerminal(taskMessage.getStatus())) {
                log.warn("Task {} already completed with status {}, skipping re-execution", id, taskMessage.getStatus());
                batchCompeteManager.submit(taskMessage, (result, ex) -> {
                    if (ex != null) log.error("complete error for re-delivered task: {}", taskMessage.getId(), ex);
                });
                handled.set(true);
                return 0;
            }

            try {
                // Check if timeout message
                if (FuTaskStatus.PENDING.equals(taskMessage.getStatus())) {
                    throw new FuTaskExpiredException("request timeout");
                }

                JSONObject output = taskExecutor.execute(taskMessage.getInput(),taskMessage.getExtInfo());

                // Verify if successful
                boolean success = assertSuccess(id, strategy, output);

                if (!success) {
                    throw new FuTaskAssertionException("assert failed");
                }
                // Execution success event handler
                onSuccess(id, strategy, output);
                // Execution completion event handler
                onComplete(id, strategy, output, null);

                taskMessage.setOutput(output);
                taskMessage.setExecuteTime(beginTime);
                taskMessage.setCompleteTime(System.currentTimeMillis());
                taskMessage.setStatus(FuTaskStatus.SUCCESS);
                taskMessage.setError(null);
                batchCompeteManager.submit(taskMessage, (result, ex) -> {
                    if (ex != null) log.error("complete error for successful task: {}", taskMessage.getId(), ex);
                });
                handled.set(true);
                return 1;

            } catch (JSONException | IllegalArgumentException | NullPointerException | ClassCastException | FuTaskAssertionException | FuTaskExpiredException | FuTaskRetryExhaustedException error) {
                // Deserialization/validation/assertion/null-reference/type-cast errors are non-retryable — retrying will always produce the same failure.
                // Fail immediately without consuming retry attempts.
                log.warn("Non-retryable error for task {}: {}", id, error.getMessage());
                onFail(id, strategy, error);
                onFinallyFail(id, strategy, error);
                onComplete(id, strategy, null, error);

                failAndComplete(taskMessage, beginTime, error.getMessage(), handled);
                return -1;

            } catch (Exception error) {

                // Re-assert interrupt flag if interrupted during execution
                if (error instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }

                // Execution failure event handler
                onFail(id, strategy, error);

                // If this is the last retry, execute failure event handler
                if (taskMessage.getRetryPlan().isEmpty() || taskMessage.getRetryTimes() >= taskMessage.getRetryPlan().size()) {

                    finallyError = error;
                    // Execution failure event handler
                    onFinallyFail(id, strategy, finallyError);
                    onComplete(id, strategy, null, finallyError);

                    failAndComplete(taskMessage, beginTime, error.getMessage(), handled);
                    return -1;

                } else {
                    // Re-enqueue
                    batchRetryManager.submit(taskMessage, (result, ex) -> {
                        if (ex != null) log.error("retry error for task: {}", taskMessage.getId(), ex);
                    });
                    handled.set(true);
                    return 0;
                }

            }
        }
    }


    private void runResetPending(int maxCount) {
        runResetSortedSet(pendingSet.getName(), "pending", monitor.pendingPoll, maxCount);
    }

    private void runResetTiming(int maxCount) {
        runResetSortedSet(timingSet.getName(), "timing", monitor.timingPoll, maxCount);
    }

    private void runResetRetry(int maxCount) {
        runResetSortedSet(retrySet.getName(), "retry", monitor.retryPoll, maxCount);
    }

    private void runResetSortedSet(String sourceSet, String label, java.util.concurrent.atomic.AtomicLong counter, int maxCount) {
        long sleepTimeMs = 0;
        int consecutiveFailures = 0;
        while (running.get()) {
            try {
                int count = doRemoveStoreSetToListScript(sourceSet, workingDeque.getName(), maxCount);
                if (count > 0) {
                    consecutiveFailures = 0;
                    sleepTimeMs = maxCount == count ? 0 : 50;
                    counter.addAndGet(count);
                    log.debug("{} poll count:{}", label, count);
                } else {
                    sleepTimeMs = sleepTimeMs == 0 ? 100 : Math.min(sleepTimeMs * 2, 1000);
                }
                if (sleep(sleepTimeMs)) break;
            } catch (Exception e) {
                consecutiveFailures++;
                log.error("runReset{} error (consecutive failures: {})", label, consecutiveFailures, e);
                long backoff = consecutiveFailures >= 5 ? Math.min(30_000L, 1000L * (1L << Math.min(consecutiveFailures - 5, 5))) : 1000L;
                if (sleep(backoff)) break;
            } catch (Error e) {
                log.error("Fatal error in runReset{}, attempting to continue", label, e);
                if (sleep(1000)) break;
            }
        }
    }


    private FuTaskWorkStrategy getStrategy(String strategy) {
        Map<String,FuTaskWorkStrategy>  strategyMap = getConfig().getStrategyMap();
        strategy = StringUtils.isBlank(strategy)? "default" : strategy;
        FuTaskWorkStrategy result = strategyMap.get(strategy);
        if (result == null && !"default".equals(strategy)) {
            log.warn("Unknown strategy '{}', falling back to 'default'", strategy);
            result = strategyMap.get("default");
        }
        return result;
    }


    protected  void onFail(String id, FuTaskWorkStrategy  strategy, Throwable error) {
        try {
            monitor.fail.incrementAndGet();
            if (strategy != null && strategy.getOnFailConsumer() != null) {
                strategy.getOnFailConsumer().accept(error, strategy);
            }
        } catch (Exception e) {
            log.error("onFail callback error", e);
        }
    }

    protected  void onFinallyFail(String id, FuTaskWorkStrategy  strategy, Throwable error) {
        try {
            monitor.finallyFail.incrementAndGet();
            if (strategy != null && strategy.getOnFinallyFailConsumer() != null) {
                strategy.getOnFinallyFailConsumer().accept(error, strategy);
            }
        } catch (Exception e) {
            log.error("onFinallyFail callback error", e);
        }
    }

    protected  void onComplete(String id, FuTaskWorkStrategy  strategy, JSONObject output, Throwable error) {
        try {
            monitor.complete.incrementAndGet();
            if (strategy != null && strategy.getOnCompleteConsumer() != null) {
                strategy.getOnCompleteConsumer().accept(output, strategy);
            }
        } catch (Exception e) {
            log.error("onComplete callback error", e);
        }

    }

    protected  void onSuccess(String id,FuTaskWorkStrategy  strategy,  JSONObject output) {
        try {
            monitor.success.incrementAndGet();
            if (strategy != null && strategy.getOnSuccessConsumer() != null) {
                strategy.getOnSuccessConsumer().accept(output, strategy);
            }
        } catch (Exception e) {
            log.error("onSuccess callback error", e);
        }
    }

    protected  boolean assertSuccess(String id ,FuTaskWorkStrategy  strategy,  JSONObject output) {
        if (strategy != null && strategy.getAssertResultFunction() != null) {
            return strategy.getAssertResultFunction().apply(output, strategy);
        }
        return true;
    }

    @Override
    public void close() {
        shutdown();
    }

    public void shutdown() {
        if (!shutdownCalled.compareAndSet(false, true)) return;
        log.info("Shutting down FuTaskWorker for topic: {}", topic);
        running.set(false);
        monitorScheduler.shutdownNow();
        try {
            if (!monitorScheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                log.warn("Monitor scheduler did not terminate in 2s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        List<ThreadPoolExecutor> daemonPools = List.of(
                subscribeThreadPool, resetPendingThreadPool,
                resetTimingThreadPool, resetRetryThreadPool);
        for (ThreadPoolExecutor pool : daemonPools) {
            pool.shutdownNow();
        }
        for (ThreadPoolExecutor pool : daemonPools) {
            try {
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Daemon pool did not terminate in 5s: {}", pool);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        workerThreadPool.shutdown();
        try {
            if (!workerThreadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Worker pool did not terminate in 30s, forcing shutdown");
                workerThreadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerThreadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        // Flush batch managers one final time to catch results submitted during grace period
        try { batchCompeteManager.flush(); } catch (Exception e) { log.warn("Error flushing complete manager on shutdown", e); }
        try { batchRetryManager.flush(); } catch (Exception e) { log.warn("Error flushing retry manager on shutdown", e); }
        // Shut down batch managers after flush so in-flight tasks can still submit results
        batchCompeteManager.shutdown();
        batchRetryManager.shutdown();
    }

}
