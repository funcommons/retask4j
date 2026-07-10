package fun.commons.retask4j.http.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConditionalOnClass(RedissonClient.class)
@ConditionalOnProperty(prefix = "retask4j.http", name = "redis.redisson")
@EnableConfigurationProperties(RedissonConfigProperties.class)
public class RedissonAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient(RedissonConfigProperties properties) throws Exception {
        return RedissonUtils.createRedissonClient(properties.getRedis());
    }

}
