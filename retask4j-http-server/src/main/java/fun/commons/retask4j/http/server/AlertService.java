package fun.commons.retask4j.http.server;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple rule-based alert evaluator. Polls overview/metrics every 30s, evaluates built-in rules,
 * and fires webhook POSTs to configured targets.
 */
@Service
@ConditionalOnProperty(prefix = "retask4j.dashboard", name = "enabled", havingValue = "true")
@Slf4j
public class AlertService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final AtomicLong alertSeq = new AtomicLong(0);
    private final Map<String, Long> ruleLastFiredAt = new ConcurrentHashMap<>();
    private final Deque<Alert> activeAlerts = new ConcurrentLinkedDeque<>();
    private final Deque<Alert> history = new ConcurrentLinkedDeque<>();
    private static final int HISTORY_LIMIT = 200;
    private static final long COOLDOWN_MS = 5 * 60 * 1000; // 5 min cooldown per rule

    @Value("${retask4j.dashboard.alert.webhook:}")
    private String webhookUrl;

    private volatile Map<String, Object> lastSnapshot;

    public synchronized void evaluate(Map<String, Object> snapshot) {
        if (snapshot == null) return;
        lastSnapshot = snapshot;
        checkAndFire("pending-too-high", "Pending > 1000",
            "pending", ((Number) snapshot.getOrDefault("pending", 0)).intValue() > 1000,
            snapshot);
        checkAndFire("any-queue-not-draining", "Queue not draining",
            "working", ((Number) snapshot.getOrDefault("working", 0)).intValue() > 100,
            snapshot);
        checkAndFire("high-fail-rate", "High fail rate",
            "failRate", highFailRate(snapshot),
            snapshot);
    }

    private boolean highFailRate(Map<String, Object> snapshot) {
        long success = ((Number) snapshot.getOrDefault("success", 0)).longValue();
        long fail = ((Number) snapshot.getOrDefault("fail", 0)).longValue();
        long total = success + fail;
        if (total < 100) return false;
        return (double) fail / total > 0.5;
    }

    private void checkAndFire(String ruleId, String ruleName, String key, boolean condition, Map<String, Object> snapshot) {
        Long lastFired = ruleLastFiredAt.get(ruleId);
        long now = System.currentTimeMillis();
        if (condition) {
            if (lastFired == null || now - lastFired > COOLDOWN_MS) {
                Alert alert = new Alert(alertSeq.incrementAndGet(), ruleId, ruleName, "WARNING", snapshot, now, false);
                activeAlerts.addFirst(alert);
                history.addFirst(alert);
                if (history.size() > HISTORY_LIMIT) history.removeLast();
                ruleLastFiredAt.put(ruleId, now);
                log.warn("Alert fired: {} - {}", ruleId, snapshot.get(key));
                fireWebhook(alert);
            }
        } else {
            // Resolve any active alerts for this rule
            activeAlerts.removeIf(a -> a.ruleId.equals(ruleId));
        }
    }

    private void fireWebhook(Alert alert) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;
        try {
            JSONObject body = new JSONObject();
            body.put("ruleId", alert.ruleId);
            body.put("ruleName", alert.ruleName);
            body.put("severity", alert.severity);
            body.put("snapshot", alert.snapshot);
            body.put("firedAt", alert.firedAt);
            restTemplate.postForEntity(webhookUrl, body.toJSONString(), String.class);
        } catch (Exception e) {
            log.warn("Failed to send alert webhook: {}", e.getMessage());
        }
    }

    public List<Alert> getActiveAlerts() {
        return new ArrayList<>(activeAlerts);
    }

    public List<Alert> getHistory() {
        return new ArrayList<>(history);
    }

    public Map<String, Object> getLastSnapshot() {
        return lastSnapshot;
    }

    public static class Alert {
        public final long id;
        public final String ruleId;
        public final String ruleName;
        public final String severity;
        public final Map<String, Object> snapshot;
        public final long firedAt;
        public final boolean resolved;

        public Alert(long id, String ruleId, String ruleName, String severity,
                     Map<String, Object> snapshot, long firedAt, boolean resolved) {
            this.id = id;
            this.ruleId = ruleId;
            this.ruleName = ruleName;
            this.severity = severity;
            this.snapshot = snapshot;
            this.firedAt = firedAt;
            this.resolved = resolved;
        }
    }
}
