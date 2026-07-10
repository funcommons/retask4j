package fun.commons.retask4j.core.internal;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public final class ThreadPoolHelper {

    private static final ThreadPoolExecutor.CallerRunsPolicy CALLER_RUNS_POLICY = new ThreadPoolExecutor.CallerRunsPolicy();

    private ThreadPoolHelper() {}

    /**
     * Creates a daemon thread pool for compute/processing tasks.
     * Uses CallerRunsPolicy for natural back-pressure: when the queue is full,
     * the caller thread runs the task, slowing submissions until capacity frees up.
     */
    public static ThreadPoolExecutor createDaemonPool(String name, int size, int queueCapacity) {
        AtomicInteger counter = new AtomicInteger(0);
        return new ThreadPoolExecutor(size, size, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(queueCapacity), r -> {
            Thread t = new Thread(r, name + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }, (r, executor) -> {
            log.warn("{} queue full, running on caller thread", name);
            CALLER_RUNS_POLICY.rejectedExecution(r, executor);
        });
    }

    /**
     * Creates a daemon thread pool for subscribe/poll loop dispatching.
     * Uses DiscardPolicy with logging: poll loop threads must never block on
     * dispatched work, as that would stall the polling loop and prevent
     * other tasks from being picked up.
     */
    public static ThreadPoolExecutor createPollDispatchPool(String name, int size, int queueCapacity) {
        AtomicInteger counter = new AtomicInteger(0);
        return new ThreadPoolExecutor(size, size, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(queueCapacity), r -> {
            Thread t = new Thread(r, name + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }, (r, executor) -> {
            log.warn("{} queue full, discarding task (poll thread must not block)", name);
        });
    }

    public static ScheduledExecutorService createDaemonScheduledExecutor(String name) {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, name + "-1");
            t.setDaemon(true);
            return t;
        });
    }
}
