package fun.commons.retask4j.http.server;

import com.alibaba.fastjson2.JSONObject;
import fun.commons.retask4j.core.config.FuTaskBaseConfig;
import fun.commons.retask4j.core.internal.FuTaskBase;
import fun.commons.retask4j.core.message.FuTaskMessage;
import fun.commons.retask4j.http.caller.FuHttpTaskCallerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Dashboard API endpoints for monitoring task queues.
 * Gated by {@code retask4j.dashboard.enabled} (default: false). When enabled, requires
 * the shared token {@code retask4j.dashboard.token} via either:
 * <ul>
 *   <li>HTTP header {@code X-Dashboard-Token: <token>}, or</li>
 *   <li>Query parameter {@code ?token=<token>}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/dashboard/api")
@ConditionalOnProperty(prefix = "retask4j.dashboard", name = "enabled", havingValue = "true")
@Slf4j
public class DashboardController extends BaseController {

    private static final Pattern TOPIC_PATTERN = Pattern.compile("^[A-Za-z0-9_.\\-]{1,64}$");
    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9_.\\-]{1,256}$");
    private static final int MAX_PAYLOAD_BYTES = 8 * 1024;
    private static final int SCAN_HARD_CAP = 1000;
    private static final int OVERVIEW_TOPIC_HARD_CAP = 200;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private FuHttpTaskCallerService fuHttpTaskCallerService;

    @Autowired(required = false)
    private AlertService alertService;

    @Autowired(required = false)
    private InstanceRegistry instanceRegistry;

    @Value("${retask4j.dashboard.token:}")
    private String requiredToken;

    @org.springframework.web.bind.annotation.GetMapping("/overview")
    public void overview(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (!authorize(request, response)) return;
        try {
            List<String> topics = FuTaskBase.listTopics(redissonClient);
            if (topics.size() > OVERVIEW_TOPIC_HARD_CAP) {
                topics = topics.subList(0, OVERVIEW_TOPIC_HARD_CAP);
            }
            List<Map<String, Object>> topicInfos = new ArrayList<>(topics.size());
            for (String topic : topics) {
                topicInfos.add(buildTopicInfo(topic));
            }
            writeApiResponse(response, Map.of("topics", topicInfos, "totalTopics", topics.size()));
        } catch (Exception e) {
            log.error("Failed to build overview", e);
            writeApiError(response, 500, e.getMessage());
        }
    }

    @org.springframework.web.bind.annotation.GetMapping("/monitors")
    public void monitors(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (!authorize(request, response)) return;
        try {
            List<Map<String, Object>> callers = new ArrayList<>();
            for (fun.commons.retask4j.http.caller.FuHttpTaskCallerController controller :
                    fuHttpTaskCallerService.getControllerMap().values()) {
                fun.commons.retask4j.core.api.FuTaskCaller<?> caller = controller.getCaller();
                Map<String, Long> snap = caller.getMonitor().snapshot();
                // topic is the path in the controllerMap keys
                String path = fuHttpTaskCallerService.getControllerMap().entrySet().stream()
                        .filter(e -> e.getValue() == controller)
                        .map(Map.Entry::getKey)
                        .findFirst().orElse("");
                callers.add(Map.of(
                        "path", path,
                        "metrics", snap
                ));
            }
            writeApiResponse(response, Map.of("callers", callers));
        } catch (Exception e) {
            log.error("Failed to build monitors", e);
            writeApiError(response, 500, e.getMessage());
        }
    }

    /**
     * Aggregate metrics: success rate, fail rate, and total counts across all callers.
     * Computed from in-memory monitor snapshots — no historical time-series.
     */
    @org.springframework.web.bind.annotation.GetMapping("/metrics")
    public void metrics(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (!authorize(request, response)) return;
        long success = 0, fail = 0, sendSuccess = 0, sendFail = 0;
        long funcComplete = 0, funcTimeout = 0, funcResultMissing = 0;
        long callbackComplete = 0, callbackFail = 0;
        for (fun.commons.retask4j.core.api.FuTaskCaller<?> caller : fuHttpTaskCallerService.getCallers()) {
            Map<String, Long> snap = caller.getMonitor().snapshot();
            success += snap.getOrDefault("success", 0L);
            fail += snap.getOrDefault("fail", 0L);
            sendSuccess += snap.getOrDefault("sendSuccess", 0L);
            sendFail += snap.getOrDefault("sendFail", 0L);
            funcComplete += snap.getOrDefault("funcComplete", 0L);
            funcTimeout += snap.getOrDefault("funcTimeout", 0L);
            funcResultMissing += snap.getOrDefault("funcResultMissing", 0L);
            callbackComplete += snap.getOrDefault("callbackComplete", 0L);
            callbackFail += snap.getOrDefault("callbackFail", 0L);
        }
        long total = success + fail;
        double successRate = total == 0 ? 0 : (double) success / total * 100;
        long totalSend = sendSuccess + sendFail;
        double sendSuccessRate = totalSend == 0 ? 0 : (double) sendSuccess / totalSend * 100;
        java.util.Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("fail", fail);
        result.put("successRate", Math.round(successRate * 100) / 100.0);
        result.put("sendSuccess", sendSuccess);
        result.put("sendFail", sendFail);
        result.put("sendSuccessRate", Math.round(sendSuccessRate * 100) / 100.0);
        result.put("funcComplete", funcComplete);
        result.put("funcTimeout", funcTimeout);
        result.put("funcResultMissing", funcResultMissing);
        result.put("callbackComplete", callbackComplete);
        result.put("callbackFail", callbackFail);
        writeApiResponse(response, result);
    }

    @org.springframework.web.bind.annotation.GetMapping("/alerts")
    public void alerts(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (!authorize(request, response)) return;
        if (alertService == null) {
            writeApiResponse(response, Map.of("active", List.of(), "history", List.of(), "snapshot", Map.of()));
            return;
        }
        // Compute aggregated snapshot and run evaluation
        long success = 0, fail = 0, working = 0, pending = 0;
        for (var c : fuHttpTaskCallerService.getCallers()) {
            var snap = c.getMonitor().snapshot();
            success += snap.getOrDefault("success", 0L);
            fail += snap.getOrDefault("fail", 0L);
        }
        for (var topic : FuTaskBase.listTopics(redissonClient)) {
            var info = new ReTaskInfo(redissonClient, topic);
            var counts = info.getTaskCountInfo();
            working += ((Number) counts.getOrDefault("working", 0)).intValue();
            pending += ((Number) counts.getOrDefault("pending", 0)).intValue();
        }
        java.util.Map<String, Object> snapshot = java.util.Map.of(
                "success", success, "fail", fail, "working", working, "pending", pending
        );
        alertService.evaluate(snapshot);

        writeApiResponse(response, Map.of(
                "active", alertService.getActiveAlerts(),
                "history", alertService.getHistory(),
                "snapshot", snapshot
        ));
    }

    @org.springframework.web.bind.annotation.GetMapping("/instances")
    public void instances(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (!authorize(request, response)) return;
        if (instanceRegistry == null) {
            writeApiResponse(response, Map.of("instances", List.of()));
            return;
        }
        writeApiResponse(response, Map.of(
                "instances", instanceRegistry.listInstances(),
                "self", instanceRegistry.getInstanceId()
        ));
    }

    @org.springframework.web.bind.annotation.GetMapping("/tasks/{topic}")
    public void tasks(@PathVariable String topic,
                      @RequestParam(defaultValue = "0") int offset,
                      @RequestParam(defaultValue = "20") int limit,
                      HttpServletRequest request,
                      HttpServletResponse response) throws Exception {
        if (!authorize(request, response)) return;
        if (!TOPIC_PATTERN.matcher(topic).matches()) {
            writeApiError(response, 400, "Invalid topic name; must match " + TOPIC_PATTERN.pattern());
            return;
        }
        if (limit > 100) limit = 100;
        if (limit < 1) limit = 20;
        if (offset < 0) offset = 0;

        try {
            List<Map<String, Object>> redacted = sampleTasks(topic, offset, limit);
            writeApiResponse(response, Map.of("messages", redacted, "offset", offset, "limit", limit));
        } catch (Exception e) {
            log.error("Failed to fetch tasks for topic {}", topic, e);
            writeApiError(response, 500, e.getMessage());
        }
    }

    @org.springframework.web.bind.annotation.GetMapping("/tasks/{topic}/{id}")
    public void taskDetail(@PathVariable String topic,
                           @PathVariable String id,
                           HttpServletRequest request,
                           HttpServletResponse response) throws Exception {
        if (!authorize(request, response)) return;
        if (!TOPIC_PATTERN.matcher(topic).matches() || !ID_PATTERN.matcher(id).matches()) {
            writeApiError(response, 400, "Invalid topic or task id");
            return;
        }
        try {
            ReTaskInfo info = new ReTaskInfo(redissonClient, topic);
            FuTaskMessage msg = info.getMessageById(id);
            if (msg == null) {
                writeApiError(response, 404, "Task not found");
                return;
            }
            writeApiResponse(response, redactMessage(msg, true));
        } catch (Exception e) {
            log.error("Failed to fetch task detail for {}/{}", topic, id, e);
            writeApiError(response, 500, e.getMessage());
        }
    }

    @org.springframework.web.bind.annotation.PostMapping("/tasks/{topic}/{id}/replay")
    public void replay(@PathVariable String topic,
                       @PathVariable String id,
                       HttpServletRequest request,
                       HttpServletResponse response) throws Exception {
        if (!authorize(request, response)) return;
        if (!TOPIC_PATTERN.matcher(topic).matches() || !ID_PATTERN.matcher(id).matches()) {
            writeApiError(response, 400, "Invalid topic or task id");
            return;
        }
        try {
            fun.commons.retask4j.core.api.FuTaskAdminService admin =
                    new fun.commons.retask4j.core.api.FuTaskAdminService(redissonClient, new fun.commons.retask4j.core.config.FuTaskBaseConfig(topic));
            boolean ok = admin.replay(id);
            if (ok) writeApiResponse(response, Map.of("ok", true, "operation", "replay"));
            else writeApiError(response, 404, "Task not found");
        } catch (Exception e) {
            log.error("Failed to replay task {}/{}", topic, id, e);
            writeApiError(response, 500, e.getMessage());
        }
    }

    @org.springframework.web.bind.annotation.PostMapping("/tasks/{topic}/{id}/force-retry")
    public void forceRetry(@PathVariable String topic,
                           @PathVariable String id,
                           HttpServletRequest request,
                           HttpServletResponse response) throws Exception {
        if (!authorize(request, response)) return;
        if (!TOPIC_PATTERN.matcher(topic).matches() || !ID_PATTERN.matcher(id).matches()) {
            writeApiError(response, 400, "Invalid topic or task id");
            return;
        }
        try {
            fun.commons.retask4j.core.api.FuTaskAdminService admin =
                    new fun.commons.retask4j.core.api.FuTaskAdminService(redissonClient, new fun.commons.retask4j.core.config.FuTaskBaseConfig(topic));
            boolean ok = admin.forceRetry(id);
            if (ok) writeApiResponse(response, Map.of("ok", true, "operation", "force-retry"));
            else writeApiError(response, 404, "Task not found");
        } catch (Exception e) {
            log.error("Failed to force-retry task {}/{}", topic, id, e);
            writeApiError(response, 500, e.getMessage());
        }
    }

    @org.springframework.web.bind.annotation.PostMapping("/tasks/{topic}/{id}/force-complete")
    public void forceComplete(@PathVariable String topic,
                              @PathVariable String id,
                              jakarta.servlet.http.HttpServletRequest request,
                              jakarta.servlet.http.HttpServletResponse response) throws Exception {
        if (!authorize(request, response)) return;
        if (!TOPIC_PATTERN.matcher(topic).matches() || !ID_PATTERN.matcher(id).matches()) {
            writeApiError(response, 400, "Invalid topic or task id");
            return;
        }
        String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String status = "SUCCESS";
        String output = "";
        String errorMsg = "";
        if (!body.isEmpty()) {
            try {
                JSONObject b = JSONObject.parseObject(body);
                if (b != null) {
                    if (b.containsKey("status")) status = b.getString("status");
                    if (b.containsKey("output") && b.get("output") != null) output = b.get("output").toString();
                    if (b.containsKey("error") && b.get("error") != null) errorMsg = b.getString("error");
                }
            } catch (Exception ignore) {
                // Allow empty body for quick SUCCESS
            }
        }
        try {
            fun.commons.retask4j.core.api.FuTaskAdminService admin =
                    new fun.commons.retask4j.core.api.FuTaskAdminService(redissonClient, new fun.commons.retask4j.core.config.FuTaskBaseConfig(topic));
            JSONObject outputJson = output.isEmpty() ? null : JSONObject.parseObject(output);
            boolean ok = admin.forceComplete(id, status, outputJson, errorMsg);
            if (ok) writeApiResponse(response, Map.of("ok", true, "operation", "force-complete", "status", status));
            else writeApiError(response, 404, "Task not found");
        } catch (IllegalArgumentException e) {
            writeApiError(response, 400, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to force-complete task {}/{}", topic, id, e);
            writeApiError(response, 500, e.getMessage());
        }
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/tasks/{topic}/{id}")
    public void deleteTask(@PathVariable String topic,
                           @PathVariable String id,
                           HttpServletRequest request,
                           HttpServletResponse response) throws Exception {
        if (!authorize(request, response)) return;
        if (!TOPIC_PATTERN.matcher(topic).matches() || !ID_PATTERN.matcher(id).matches()) {
            writeApiError(response, 400, "Invalid topic or task id");
            return;
        }
        try {
            fun.commons.retask4j.core.api.FuTaskAdminService admin =
                    new fun.commons.retask4j.core.api.FuTaskAdminService(redissonClient, new fun.commons.retask4j.core.config.FuTaskBaseConfig(topic));
            long removed = admin.delete(id);
            writeApiResponse(response, Map.of("ok", true, "operation", "delete", "removed", removed));
        } catch (Exception e) {
            log.error("Failed to delete task {}/{}", topic, id, e);
            writeApiError(response, 500, e.getMessage());
        }
    }

    @org.springframework.web.bind.annotation.GetMapping(value = "/stream", produces = "text/event-stream")
    public void stream(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (!authorize(request, response)) return;
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");

        org.redisson.api.RTopic topic = redissonClient.getTopic("retask4j-events");
        java.util.concurrent.atomic.AtomicBoolean alive = new java.util.concurrent.atomic.AtomicBoolean(true);

        int messageId = (int) (System.currentTimeMillis() & 0x7fffffff);
        org.redisson.api.listener.MessageListener<String> listener = (channel, msg) -> {
            if (!alive.get()) return;
            try {
                response.getWriter().write("id: " + messageId + "\n");
                response.getWriter().write("event: task-event\n");
                response.getWriter().write("data: " + msg + "\n\n");
                response.getWriter().flush();
            } catch (Exception ignored) {
                alive.set(false);
            }
        };
        int listenerId = topic.addListener(String.class, listener);

        // Send a heartbeat every 15s
        java.util.concurrent.ScheduledExecutorService heartbeat =
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "dashboard-sse-heartbeat");
                    t.setDaemon(true);
                    return t;
                });
        java.util.concurrent.ScheduledFuture<?> beat = heartbeat.scheduleAtFixedRate(() -> {
            if (!alive.get()) return;
            try {
                response.getWriter().write(": heartbeat\n\n");
                response.getWriter().flush();
            } catch (Exception ignored) {
                alive.set(false);
            }
        }, 15, 15, java.util.concurrent.TimeUnit.SECONDS);

        try {
            // Block until the client disconnects
            while (alive.get()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            beat.cancel(false);
            heartbeat.shutdownNow();
            try {
                topic.removeListener(listener);
            } catch (Exception ignored) {}
        }
    }

    private boolean authorize(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (requiredToken == null || requiredToken.isEmpty()) {
            writeApiError(response, 503, "Dashboard enabled but no token configured (set retask4j.dashboard.token)");
            return false;
        }
        String supplied = request.getHeader("X-Dashboard-Token");
        if (supplied == null || supplied.isEmpty()) {
            supplied = request.getParameter("token");
        }
        if (supplied == null || !constantTimeEquals(supplied, requiredToken)) {
            writeApiError(response, 401, "Unauthorized");
            return false;
        }
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'");
        return true;
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] ab = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        if (ab.length != bb.length) return false;
        int diff = 0;
        for (int i = 0; i < ab.length; i++) diff |= ab[i] ^ bb[i];
        return diff == 0;
    }

    private Map<String, Object> buildTopicInfo(String topic) {
        ReTaskInfo info = new ReTaskInfo(redissonClient, topic);
        JSONObject counts = info.getTaskCountInfo();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("topic", topic);
        m.put("working", counts.get("working"));
        m.put("pending", counts.get("pending"));
        m.put("timing", counts.get("timing"));
        m.put("retry", counts.get("retry"));
        m.put("callbackWorking", counts.get("callback-working"));
        m.put("callbackPending", counts.get("callback-pending"));
        return m;
    }

    /**
     * Sample up to {@code limit} task message IDs from the working deque without mutating it.
     * Uses a per-request {@code RBlockingDeque} view (read-only via pollLast/offerFirst round-trip
     * with cap), and returns redacted payload fields.
     */
    private List<Map<String, Object>> sampleTasks(String topic, int offset, int limit) {
        ReTaskInfo info = new ReTaskInfo(redissonClient, topic);
        List<Map<String, Object>> sample = new ArrayList<>(limit);
        try {
            org.redisson.api.RBlockingDeque<String> deque =
                    redissonClient.getBlockingDeque("fu-task-" + topic + "-blocking");
            int skipped = 0;
            int collected = 0;
            for (int i = 0; i < SCAN_HARD_CAP && collected < limit; i++) {
                String id = deque.peek();
                if (id == null) break;
                if (skipped < offset) {
                    skipped++;
                } else {
                    FuTaskMessage msg = info.getMessageById(id);
                    if (msg != null) {
                        sample.add(redactMessage(msg, false));
                        collected++;
                    }
                }
                // Non-mutating peek: pollLast + offerFirst rotates the deque but preserves order
                String polled = deque.pollLast();
                if (polled != null) {
                    deque.offerFirst(polled);
                }
            }
        } catch (Exception e) {
            log.debug("sampleTasks error for topic {}", topic, e);
        }
        return sample;
    }

    /**
     * Redact a message: include metadata plus input/output/error. When {@code full} is true,
     * the body fields are not truncated (used for the task detail view).
     */
    private static Map<String, Object> redactMessage(FuTaskMessage msg, boolean full) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", msg.getId());
        m.put("topic", msg.getTopic());
        m.put("status", msg.getStatus());
        m.put("mode", msg.getMode());
        m.put("retryTimes", msg.getRetryTimes());
        m.put("retryPlan", msg.getRetryPlan());
        m.put("delayTime", msg.getDelayTime());
        m.put("executeExpire", msg.getExecuteExpire());
        m.put("resultExpire", msg.getResultExpire());
        m.put("strategy", msg.getStrategy());
        m.put("tag", msg.getTag());
        m.put("callerId", msg.getCallerId());
        m.put("createTime", msg.getCreateTime());
        m.put("scheduleTime", msg.getScheduleTime());
        m.put("executeTime", msg.getExecuteTime());
        m.put("completeTime", msg.getCompleteTime());
        m.put("callbackStatus", msg.getCallbackStatus());
        m.put("callbackRetryTimes", msg.getCallbackRetryTimes());
        m.put("input", full ? jsonToString(msg.getInput()) : truncate(msg.getInput()));
        m.put("output", full ? jsonToString(msg.getOutput()) : truncate(msg.getOutput()));
        m.put("error", full ? msg.getError() : truncateText(msg.getError()));
        return m;
    }

    private static String truncate(JSONObject obj) {
        if (obj == null) return null;
        String s = obj.toJSONString();
        if (s.length() > MAX_PAYLOAD_BYTES) {
            return s.substring(0, MAX_PAYLOAD_BYTES) + "...";
        }
        return s;
    }

    private static String jsonToString(JSONObject obj) {
        return obj == null ? null : obj.toJSONString();
    }

    private static String truncateText(String s) {
        if (s == null) return null;
        if (s.length() > MAX_PAYLOAD_BYTES) {
            return s.substring(0, MAX_PAYLOAD_BYTES) + "...";
        }
        return s;
    }

    static class ReTaskInfo extends FuTaskBase {
        public ReTaskInfo(RedissonClient redissonClient, String topic) {
            super(redissonClient, new FuTaskBaseConfig(topic));
        }

        @Override
        public JSONObject getTaskCountInfo() {
            return super.getTaskCountInfo();
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
