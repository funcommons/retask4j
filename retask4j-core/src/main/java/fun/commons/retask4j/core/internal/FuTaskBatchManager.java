package fun.commons.retask4j.core.internal;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FuTaskBatchManager<T, R> {

    private static final int DEFAULT_MAX_QUEUE_CAPACITY = 100_000;

    private final int batchSize;
    private final long batchInterval;
    private final int maxProcessThreads;
    private final String name;

    private final Function<List<T>, R> batchFunction;

    private final AtomicBoolean running = new AtomicBoolean(true);

    // Scheduled task thread pool manager
    private final ScheduledExecutorService checkExecutorService;
    private final ThreadPoolExecutor processThreadPool;

    private final BlockingQueue<Map.Entry<T, CompletableFuture<R>>> taskQueue;
    private final AtomicLong lastProcessing = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger activeProcessCount = new AtomicInteger(0);

    public FuTaskBatchManager(Function<List<T>, R> batchFunction) {
        this(1000, 20, 4, "batch", batchFunction);
    }

    public FuTaskBatchManager(int batchSize, long batchInterval, int maxProcessThreads, Function<List<T>, R> batchFunction) {
        this(batchSize, batchInterval, maxProcessThreads, "batch", batchFunction);
    }

    public FuTaskBatchManager(int batchSize, long batchInterval, int maxProcessThreads, String name, Function<List<T>, R> batchFunction) {
        this(batchSize, batchInterval, maxProcessThreads, name, DEFAULT_MAX_QUEUE_CAPACITY, batchFunction);
    }

    public FuTaskBatchManager(int batchSize, long batchInterval, int maxProcessThreads, String name, int maxQueueCapacity, Function<List<T>, R> batchFunction) {
        this.batchSize = batchSize;
        this.batchInterval = batchInterval;
        this.batchFunction = batchFunction;
        this.maxProcessThreads = maxProcessThreads;
        this.name = name;
        this.taskQueue = new LinkedBlockingQueue<>(maxQueueCapacity);
        checkExecutorService = ThreadPoolHelper.createDaemonScheduledExecutor(name + "-check");
        processThreadPool = ThreadPoolHelper.createDaemonPool(name + "-process", maxProcessThreads, 10000);
        checkExecutorService.scheduleWithFixedDelay(this::onScheduleTime, 10, 10, TimeUnit.MILLISECONDS);
    }

    public CompletableFuture<R> submit(T data) {
        CompletableFuture<R> future = new CompletableFuture<>();
        if (!running.get()) {
            future.completeExceptionally(new RuntimeException("BatchManager is shut down"));
            return future;
        }
        Map.Entry<T, CompletableFuture<R>> task = Map.entry(data, future);
        if (!taskQueue.offer(task)) {
            future.completeExceptionally(new RuntimeException("BatchManager queue full"));
            return future;
        }
        // Re-check after offer: if shutdown started, drain and fail this task
        if (!running.get()) {
            if (taskQueue.remove(task)) {
                future.completeExceptionally(new RuntimeException("BatchManager is shut down"));
            }
        }
        return future;
    }

    public CompletableFuture<R> submit(T data, BiConsumer<R, ? super Throwable> onComplete) {
        CompletableFuture<R> future = new CompletableFuture<>();
        if (!running.get()) {
            future.completeExceptionally(new RuntimeException("BatchManager is shut down"));
            return future;
        }
        try {
            future.whenCompleteAsync(onComplete, processThreadPool);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            future.whenComplete(onComplete);
        }
        Map.Entry<T, CompletableFuture<R>> task = Map.entry(data, future);
        if (!taskQueue.offer(task)) {
            future.completeExceptionally(new RuntimeException("BatchManager queue full"));
            return future;
        }
        // Re-check after offer: if shutdown started, drain and fail this task
        if (!running.get()) {
            if (taskQueue.remove(task)) {
                future.completeExceptionally(new RuntimeException("BatchManager is shut down"));
            }
        }
        return future;
    }

    public int getTaskCount() {
        return taskQueue.size();
    }

    public int getWorkerCount() {
        return activeProcessCount.get();
    }

    private void onScheduleTime() {
        try {
            if (!running.get()) return;
            boolean isTime = System.currentTimeMillis() - lastProcessing.get() > batchInterval;
            boolean isFull = taskQueue.size() >= batchSize;
            // Trigger processing when time is up and queue is non-empty, or when queue is full
            if ((isTime && !taskQueue.isEmpty()) || isFull) {
                // Atomically check and reserve a process slot to prevent exceeding maxProcessThreads
                int current;
                do {
                    current = activeProcessCount.get();
                    if (current >= maxProcessThreads) return;
                } while (!activeProcessCount.compareAndSet(current, current + 1));
                batchProcessing();
            }
        } catch (Exception e) {
            log.error("Unexpected error in scheduled batch check, will retry on next cycle", e);
        }
    }

    private void batchProcessing() {

        List<Map.Entry<T, CompletableFuture<R>>> batch = new ArrayList<>();
        try {
            taskQueue.drainTo(batch, batchSize);
        } catch (Exception e) {
            // drainTo threw (e.g. InterruptedException); release the slot reserved by onScheduleTime()
            activeProcessCount.decrementAndGet();
            log.error("Failed to drain batch queue", e);
            return;
        }
        if (!batch.isEmpty()) {
            lastProcessing.set(System.currentTimeMillis());
            // activeProcessCount was already incremented by the CAS in onScheduleTime()
            List<T> dataList = new ArrayList<>();
            for (Map.Entry<T, CompletableFuture<R>> task : batch) {
                dataList.add(task.getKey());
            }
            try {
                processThreadPool.execute(() -> {
                    try {
                        R result = batchFunction.apply(dataList);
                        for (Map.Entry<T, CompletableFuture<R>> task : batch) {
                            task.getValue().complete(result);
                        }
                    } catch (Throwable e) {
                        for (Map.Entry<T, CompletableFuture<R>> task : batch) {
                            task.getValue().completeExceptionally(e);
                        }
                        if (e instanceof OutOfMemoryError) throw (OutOfMemoryError) e;
                    } finally {
                        activeProcessCount.decrementAndGet();
                    }
                });
            } catch (java.util.concurrent.RejectedExecutionException e) {
                for (Map.Entry<T, CompletableFuture<R>> task : batch) {
                    task.getValue().completeExceptionally(new RuntimeException("Process pool rejected execution", e));
                }
                activeProcessCount.decrementAndGet();
            }
        } else {
            // Nothing to process, release the slot reserved by onScheduleTime()
            activeProcessCount.decrementAndGet();
        }
    }

    /**
     * Flush remaining items synchronously before shutdown.
     * Processes all queued items inline to prevent data loss.
     * @throws RuntimeException if flushing exceeds the timeout
     */
    public void flush() {
        flush(30, TimeUnit.SECONDS);
    }

    public void flush(long timeout, TimeUnit unit) {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        List<Map.Entry<T, CompletableFuture<R>>> batch = new ArrayList<>();
        taskQueue.drainTo(batch, batchSize);
        while (!batch.isEmpty()) {
            if (System.nanoTime() > deadline) {
                log.warn("Flush timed out with {} items remaining, failing them", batch.size());
                for (Map.Entry<T, CompletableFuture<R>> task : batch) {
                    task.getValue().completeExceptionally(new RuntimeException("Flush timed out"));
                }
                break;
            }
            List<T> dataList = new ArrayList<>();
            for (Map.Entry<T, CompletableFuture<R>> task : batch) {
                dataList.add(task.getKey());
            }
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                log.warn("Flush deadline exceeded before processing batch of {} items", batch.size());
                for (Map.Entry<T, CompletableFuture<R>> task : batch) {
                    task.getValue().completeExceptionally(new RuntimeException("Flush timed out"));
                }
                break;
            }
            try {
                // Execute inline during flush to avoid CallerRunsPolicy blocking on a full pool.
                // Flush is called during shutdown when the pool may be near capacity.
                R result = batchFunction.apply(dataList);
                for (Map.Entry<T, CompletableFuture<R>> task : batch) {
                    task.getValue().complete(result);
                }
            } catch (Throwable e) {
                for (Map.Entry<T, CompletableFuture<R>> task : batch) {
                    task.getValue().completeExceptionally(e);
                }
            }
            batch.clear();
            taskQueue.drainTo(batch, batchSize);
        }
    }

    public void shutdown() {
        if (!running.compareAndSet(true, false)) return;
        // Stop scheduler first so no new batches are triggered
        checkExecutorService.shutdownNow();
        // Flush remaining items BEFORE shutting down the process pool,
        // since flush() submits work to the pool
        flush();
        processThreadPool.shutdown();
        try {
            if (!processThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("BatchManager process pool did not terminate in 5s, forcing shutdown");
                processThreadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            processThreadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        List<Map.Entry<T, CompletableFuture<R>>> remaining = new ArrayList<>();
        taskQueue.drainTo(remaining, batchSize);
        while (!remaining.isEmpty()) {
            for (Map.Entry<T, CompletableFuture<R>> entry : remaining) {
                entry.getValue().completeExceptionally(new RuntimeException("BatchManager shut down"));
            }
            remaining.clear();
            taskQueue.drainTo(remaining, batchSize);
        }
    }

}
