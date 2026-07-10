package fun.commons.retask4j.http.worker;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.WebApplicationContext;

@Slf4j
@Configuration
@ConditionalOnClass(RedissonClient.class)
@EnableConfigurationProperties(FuHttpTaskWorkerProperties.class)
@ConditionalOnProperty(prefix = "retask4j.http", name = "workers")
@AutoConfigureAfter(name = "org.redisson.spring.starter.RedissonAutoConfiguration")
public class FuHttpTaskWorkerAutoConfiguration {

    private final FuHttpTaskWorkerProperties properties;
    private final WebApplicationContext applicationContext;
    private final RedissonClient redissonClient;

    public FuHttpTaskWorkerAutoConfiguration(FuHttpTaskWorkerProperties properties,
                                              WebApplicationContext applicationContext,
                                              RedissonClient redissonClient) {
        this.properties = properties;
        this.applicationContext = applicationContext;
        this.redissonClient = redissonClient;
        log.info("Initialize FuHttpTaskWorkerAutoConfiguration");
    }

    @Bean(destroyMethod = "destroy")
    @ConditionalOnMissingBean(FuHttpTaskWorkerService.class)
    FuHttpTaskWorkerService genFuHttpTaskWorkerService() throws Exception {

        log.info("Initialize FuHttpTaskWorkerService");

        if (properties.getRedis() == null || !properties.getRedis().containsKey("redisson")){
            throw new IllegalStateException("retask4j.http.redis.redisson is required when workers is configured");
        }

        return new FuHttpTaskWorkerService(properties, applicationContext, redissonClient);

    }


}
