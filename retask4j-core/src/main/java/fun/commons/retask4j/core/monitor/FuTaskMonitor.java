package fun.commons.retask4j.core.monitor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class FuTaskMonitor {

    private FuTaskMonitor() {}

    public static final class WorkerMonitor {
        public final AtomicLong consume = new AtomicLong(0);
        public final AtomicLong workerCompleted = new AtomicLong(0);
        public final AtomicLong success = new AtomicLong(0);
        public final AtomicLong finallyFail = new AtomicLong(0);
        public final AtomicLong expired = new AtomicLong(0);
        public final AtomicLong retryExhausted = new AtomicLong(0);
        public final AtomicLong complete = new AtomicLong(0);
        public final AtomicLong fail = new AtomicLong(0);
        public final AtomicLong timingPoll = new AtomicLong(0);
        public final AtomicLong pendingPoll = new AtomicLong(0);
        public final AtomicLong retryPoll = new AtomicLong(0);
        public final AtomicLong workerActiveCount = new AtomicLong(0);

        public Map<String, Long> snapshot() {
            Map<String, Long> snap = new LinkedHashMap<>(12);
            snap.put("consume", consume.get());
            snap.put("workerCompleted", workerCompleted.get());
            snap.put("success", success.get());
            snap.put("finallyFail", finallyFail.get());
            snap.put("expired", expired.get());
            snap.put("retryExhausted", retryExhausted.get());
            snap.put("complete", complete.get());
            snap.put("fail", fail.get());
            snap.put("timingPoll", timingPoll.get());
            snap.put("pendingPoll", pendingPoll.get());
            snap.put("retryPoll", retryPoll.get());
            snap.put("workerActiveCount", workerActiveCount.get());
            return Collections.unmodifiableMap(snap);
        }
    }

    public static final class CallerMonitor {
        public final AtomicLong funcComplete = new AtomicLong(0);
        public final AtomicLong funcTimeout = new AtomicLong(0);
        public final AtomicLong funcResultMissing = new AtomicLong(0);
        public final AtomicLong callbackComplete = new AtomicLong(0);
        public final AtomicLong callbackFail = new AtomicLong(0);
        public final AtomicLong sendSuccess = new AtomicLong(0);
        public final AtomicLong sendFail = new AtomicLong(0);
        public final AtomicLong cacheEviction = new AtomicLong(0);

        public Map<String, Long> snapshot() {
            Map<String, Long> snap = new LinkedHashMap<>(8);
            snap.put("funcComplete", funcComplete.get());
            snap.put("funcTimeout", funcTimeout.get());
            snap.put("funcResultMissing", funcResultMissing.get());
            snap.put("callbackComplete", callbackComplete.get());
            snap.put("callbackFail", callbackFail.get());
            snap.put("sendSuccess", sendSuccess.get());
            snap.put("sendFail", sendFail.get());
            snap.put("cacheEviction", cacheEviction.get());
            return Collections.unmodifiableMap(snap);
        }
    }
}
