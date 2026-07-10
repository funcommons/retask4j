package fun.commons.retask4j.http.caller;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Slf4j
@Configuration
@ConditionalOnClass(RedissonClient.class)
@EnableConfigurationProperties(FuHttpTaskCallerProperties.class)
@ConditionalOnProperty(prefix = "retask4j.http", name = "callers")
@AutoConfigureAfter(name = "org.redisson.spring.starter.RedissonAutoConfiguration")
public class FuHttpTaskCallerAutoConfiguration {

    private final FuHttpTaskCallerProperties properties;
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;
    private final RedissonClient redissonClient;

    public FuHttpTaskCallerAutoConfiguration(FuHttpTaskCallerProperties properties,
                                              RequestMappingHandlerMapping requestMappingHandlerMapping,
                                              RedissonClient redissonClient) {
        this.properties = properties;
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
        this.redissonClient = redissonClient;
        log.info("Initialize FuHttpTaskCallerAutoConfiguration");
    }

    @Bean(destroyMethod = "destroy")
    @ConditionalOnMissingBean(FuHttpTaskCallerService.class)
    FuHttpTaskCallerService genFuHttpTaskCallerService() throws Exception {

        log.info("Initialize FuHttpTaskCallerService");

        if (properties.getRedis() == null || !properties.getRedis().containsKey("redisson")){
            throw new IllegalStateException("retask4j.http.redis.redisson is required when callers is configured");
        }

        return new FuHttpTaskCallerService(properties, requestMappingHandlerMapping, redissonClient);

    }

}
