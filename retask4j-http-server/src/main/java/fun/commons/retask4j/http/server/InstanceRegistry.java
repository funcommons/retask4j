package fun.commons.retask4j.http.server;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Registers this dashboard instance in a shared Redis key so multiple instances can be
 * observed via the /instances API. Heartbeats every 10s with TTL 30s; instances that miss
 * 3+ heartbeats are filtered out.
 */
@Service
@ConditionalOnProperty(prefix = "retask4j.dashboard", name = "enabled", havingValue = "true")
@EnableScheduling
@Slf4j
public class InstanceRegistry {

    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);
    private final long startTime = System.currentTimeMillis();

    @Autowired
    private RedissonClient redisson;

    @Value("${spring.application.name:retask4j-dashboard}")
    private String appName;

    @Value("${server.port:9090}")
    private int port;

    private static final String KEY = "retask4j-instances";

    @Scheduled(fixedRate = 10000)
    public void heartbeat() {
        try {
            JSONObject info = new JSONObject();
            info.put("instanceId", instanceId);
            info.put("appName", appName);
            info.put("port", port);
            info.put("startTime", startTime);
            info.put("heartbeatAt", System.currentTimeMillis());
            info.put("jvmUptime", ManagementFactory.getRuntimeMXBean().getUptime());
            info.put("availableProcessors", ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors());

            redisson.getBucket(KEY + ":" + instanceId).set(info.toJSONString(), java.time.Duration.ofSeconds(30));
        } catch (Exception e) {
            log.debug("Instance heartbeat failed", e);
        }
    }

    public List<JSONObject> listInstances() {
        List<JSONObject> out = new ArrayList<>();
        try {
            Iterable<String> keys = redisson.getKeys().getKeysByPattern(KEY + ":*", 100);
            long now = System.currentTimeMillis();
            for (String k : keys) {
                Object v = redisson.getBucket(k).get();
                if (v == null) continue;
                try {
                    JSONObject info = JSONObject.parseObject(v.toString());
                    long hb = info.getLong("heartbeatAt");
                    if (now - hb > 30_000) continue; // stale
                    info.put("alive", true);
                    info.put("ageMs", now - info.getLong("startTime"));
                    info.put("sinceLastHeartbeatMs", now - hb);
                    out.add(info);
                } catch (Exception ignore) {}
            }
        } catch (Exception e) {
            log.debug("listInstances failed", e);
        }
        return out;
    }

    public String getInstanceId() {
        return instanceId;
    }
}
