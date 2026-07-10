package fun.commons.retask4j.http.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.util.Map;

@Slf4j
public final class RedissonUtils {

    private RedissonUtils() {}

    public static RedissonClient createRedissonClient(Map<String, Object> redisConfig) throws Exception {
        Object redissonConfig = redisConfig.get("redisson");
        if (redissonConfig == null) {
            throw new IllegalStateException("retask4j.http.redis.redisson is required");
        }
        if (!(redissonConfig instanceof Map)) {
            throw new IllegalStateException("retask4j.http.redis.redisson must be a map, got: " + redissonConfig.getClass().getName());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> configMap = (Map<String, Object>) redissonConfig;
        log.info("Creating RedissonClient with {} top-level config keys", configMap.size());
        log.debug("RedissonClient config: {}", JSON.toJSONString(configMap, JSONWriter.Feature.PrettyFormat));
        String yml = new ObjectMapper(new YAMLFactory()).writeValueAsString(configMap);
        Config config = Config.fromYAML(yml);
        return Redisson.create(config);
    }

}
