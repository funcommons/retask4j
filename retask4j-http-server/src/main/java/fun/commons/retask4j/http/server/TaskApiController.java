package fun.commons.retask4j.http.server;

import com.alibaba.fastjson2.JSONObject;
import fun.commons.retask4j.core.api.FuTaskAdminService;
import fun.commons.retask4j.core.api.FuTaskSubmitter;
import fun.commons.retask4j.core.config.FuTaskBaseConfig;
import fun.commons.retask4j.core.internal.FuTaskBase;
import fun.commons.retask4j.core.message.FuTaskMessage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * REST API for non-Java clients (Python, Go, Node.js, etc.) to submit, query, and
 * pull tasks from retask4j over HTTP. Authenticated via {@code retask4j.api.token}
 * (separate from the dashboard token).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/submit} — submit a task to a topic</li>
 *   <li>{@code GET /api/tasks/{topic}/{id}} — peek task state, input, output, error</li>
 *   <li>{@code GET /api/pull/{topic}} — pull-mode worker: claim next task from queue</li>
 *   <li>{@code POST /api/complete/{topic}/{id}} — pull-mode worker: report completion</li>
 *   <li>{@code GET /api/topics} — list active topics</li>
 *   <li>{@code GET /api/queues/{topic}} — get queue depths</li>
 * </ul>
 *
 * <p>Auth: header {@code X-Api-Token: <token>} or query param {@code ?token=<token>}.
 */
@RestController
@RequestMapping("/api")
@ConditionalOnProperty(prefix = "retask4j.api", name = "enabled", havingValue = "true")
@Slf4j
public class TaskApiController extends BaseController {

    private static final Pattern TOPIC_PATTERN = Pattern.compile("^[A-Za-z0-9_.\\-]{1,64}$");
    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9_.\\-]{1,256}$");
    private static final long MAX_BODY_BYTES = 64 * 1024;
    private static final long CLAIM_LEASE_MS = 60_000;

    @Autowired
    private RedissonClient redissonClient;

    @Value("${retask4j.api.token:}")
    private String requiredToken;

    @org.springframework.web.bind.annotation.PostMapping("/submit")
    public void submit(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (!authorize(request, response)) return;
        String body = readBody(request);
        if (body.isEmpty()) {
            writeApiError(response, 400, "Request body must not be empty");
            return;
        }
        FuTaskSubmitter.SubmitRequest req;
        try {
            req = com.alibaba.fastjson2.JSON.to(FuTaskSubmitter.SubmitRequest.class, body);
        } catch (Exception e) {
            writeApiError(response, 400, "Invalid JSON: " + e.getMessage());
            return;
        }
        if (req == null) {
            writeApiError(response, 400, "Empty JSON");
            return;
        }
        String topic = req.topicFromBody();
        if (topic == null || !TOPIC_PATTERN.matcher(topic).matches()) {
            writeApiError(response, 400, "Field 'topic' is required and must match " + TOPIC_PATTERN.pattern());
            return;
        }
        if (req.id != null && !ID_PATTERN.matcher(req.id).matches()) {
            writeApiError(response, 400, "Field 'id' must match " + ID_PATTERN.pattern());
            return;
        }
        req.topic = topic;
        try {
            FuTaskSubmitter submitter = new FuTaskSubmitter(redissonClient, topic);
            FuTaskMessage msg = submitter.submit(req);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", msg.getId());
            result.put("topic", msg.getTopic());
            result.put("mode", msg.getMode());
            result.put("status", msg.getStatus());
            result.put("createTime", msg.getCreateTime());
            if (msg.getDelayTime() > 0) {
                result.put("scheduledAt", msg.getCreateTime() + (long) msg.getDelayTime() * 1000);
            }
            writeApiResponse(response, result);
        } catch (Exception e) {
            log.error("submit error", e);
            writeApiError(response, 500, e.getMessage());
        }
    }

    @org.springframework.web.bind.annotation.GetMapping("/tasks/{topic}/{id}")
    public void taskStatus(@PathVariable String topic,
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
            writeApiResponse(response, taskToMap(msg));
        } catch (Exception e) {
            log.error("taskStatus error", e);
            writeApiError(response, 500, e.getMessage());
        }
    }

    @org.springframework.web.bind.annotation.GetMapping("/pull/{topic}")
    public void pull(@PathVariable String topic,
                     @org.springframework.web.bind.annotation.RequestParam(value = "wait", defaultValue = "0") int waitSeconds,
                     HttpServletRequest request,
                     HttpServletResponse response) throws Exception {
        if (!authorize(request, response)) return;
        if (!TOPIC_PATTERN.matcher(topic).matches()) {
            writeApiError(response, 400, "Invalid topic");
            return;
        }
        if (waitSeconds < 0) waitSeconds = 0;
        if (waitSeconds > 30) waitSeconds = 30;
        try {
            String prefix = "fu-task-" + topic + "-";
            org.redisson.api.RBlockingDeque<String> working = redissonClient.getBlockingDeque(prefix + "blocking");
            String id;
            if (waitSeconds == 0) {
                id = working.poll();
            } else {
                id = working.poll(waitSeconds, java.util.concurrent.TimeUnit.SECONDS);
            }
            if (id == null) {
                response.setStatus(204);
                return;
            }
            // Move to pending set with a lease timeout
            org.redisson.api.RScoredSortedSet<String> pending = redissonClient.getScoredSortedSet(prefix + "pending");
            pending.add(System.currentTimeMillis() + CLAIM_LEASE_MS, id);
            // Bump message status
            ReTaskInfo info = new ReTaskInfo(redissonClient, topic);
            FuTaskMessage msg = info.getMessageById(id);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("topic", topic);
            result.put("leaseUntilMs", System.currentTimeMillis() + CLAIM_LEASE_MS);
            if (msg != null) {
                result.put("mode", msg.getMode());
                result.put("retryTimes", msg.getRetryTimes());
                result.put("createTime", msg.getCreateTime());
                result.put("input", msg.getInput());
                if (msg.getRetryPlan() != null && !msg.getRetryPlan().isEmpty()) {
                    result.put("retryPlan", msg.getRetryPlan());
                }
            }
            writeApiResponse(response, result);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            response.setStatus(204);
        } catch (Exception e) {
            log.error("pull error", e);
            writeApiError(response, 500, e.getMessage());
        }
    }

    @org.springframework.web.bind.annotation.PostMapping("/complete/{topic}/{id}")
    public void complete(@PathVariable String topic,
                         @PathVariable String id,
                         HttpServletRequest request,
                         HttpServletResponse response) throws Exception {
        if (!authorize(request, response)) return;
        if (!TOPIC_PATTERN.matcher(topic).matches() || !ID_PATTERN.matcher(id).matches()) {
            writeApiError(response, 400, "Invalid topic or task id");
            return;
        }
        String body = readBody(request);
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
            } catch (Exception ignore) {}
        }
        try {
            FuTaskAdminService admin = new FuTaskAdminService(redissonClient, new FuTaskBaseConfig(topic));
            JSONObject outputJson = output.isEmpty() ? null : JSONObject.parseObject(output);
            boolean ok = admin.forceComplete(id, status, outputJson, errorMsg);
            if (ok) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("ok", true);
                result.put("id", id);
                result.put("status", status);
                writeApiResponse(response, result);
            } else {
                writeApiError(response, 404, "Task not found");
            }
        } catch (Exception e) {
            log.error("complete error", e);
            writeApiError(response, 500, e.getMessage());
        }
    }

    @org.springframework.web.bind.annotation.GetMapping("/topics")
    public void topics(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (!authorize(request, response)) return;
        try {
            List<String> topics = FuTaskBase.listTopics(redissonClient);
            writeApiResponse(response, Map.of("topics", topics, "totalTopics", topics.size()));
        } catch (Exception e) {
            log.error("topics error", e);
            writeApiError(response, 500, e.getMessage());
        }
    }

    @org.springframework.web.bind.annotation.GetMapping("/queues/{topic}")
    public void queues(@PathVariable String topic,
                       HttpServletRequest request,
                       HttpServletResponse response) throws Exception {
        if (!authorize(request, response)) return;
        if (!TOPIC_PATTERN.matcher(topic).matches()) {
            writeApiError(response, 400, "Invalid topic");
            return;
        }
        try {
            ReTaskInfo info = new ReTaskInfo(redissonClient, topic);
            writeApiResponse(response, info.getTaskCountInfo());
        } catch (Exception e) {
            log.error("queues error", e);
            writeApiError(response, 500, e.getMessage());
        }
    }

    // ----- helpers -----

    private boolean authorize(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (requiredToken == null || requiredToken.isEmpty()) {
            writeApiError(response, 503, "API disabled: set retask4j.api.token to enable");
            return false;
        }
        String supplied = request.getHeader("X-Api-Token");
        if (supplied == null || supplied.isEmpty()) {
            supplied = request.getParameter("token");
        }
        if (supplied == null || !constantTimeEquals(supplied, requiredToken)) {
            writeApiError(response, 401, "Unauthorized");
            return false;
        }
        response.setHeader("X-Content-Type-Options", "nosniff");
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

    private String readBody(HttpServletRequest request) throws java.io.IOException {
        byte[] bytes = request.getInputStream().readNBytes((int) MAX_BODY_BYTES);
        if (bytes.length == MAX_BODY_BYTES) {
            log.warn("Request body at limit ({} bytes); truncating", MAX_BODY_BYTES);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static Map<String, Object> taskToMap(FuTaskMessage msg) {
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
        m.put("input", msg.getInput());
        m.put("output", msg.getOutput());
        m.put("error", msg.getError());
        m.put("callbackStatus", msg.getCallbackStatus());
        return m;
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
