package fun.commons.retask4j.demo.caller.config;


import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import fun.commons.retask4j.http.config.HttpClientFactory;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Map;


@Slf4j
@Configuration
@EnableConfigurationProperties(MainProperties.class)
public class MainConfiguration implements InitializingBean {

    @Autowired
    private MainProperties mainProperties;

    public MainConfiguration() {
        log.info("Initialize Constructor");
    }

    @Bean(destroyMethod = "close")
    public HttpClientFactory.HttpClientHolder httpClientHolder() {
        return HttpClientFactory.create(60_000, 120_000);
    }

    @Bean("rest-template")
    public RestTemplate getRestTemplate(HttpClientFactory.HttpClientHolder httpClientHolder) {
        log.info("Initialize getRestTemplate");
        return httpClientHolder.getRestTemplate();
    }

    @Bean(name = "redisson-client", destroyMethod = "shutdown")
    public RedissonClient getRedissonClient() throws IOException {
        log.info("Initialize getRedissonClient");
        Map<String, Object> configMap = (Map<String, Object>) mainProperties.getRedis().get("redisson");
        log.debug("Redisson config keys: {}", configMap != null ? configMap.keySet() : "null");
        String yml = new ObjectMapper(new YAMLFactory()).writeValueAsString(configMap);
        Config config = Config.fromYAML(yml);

        RedissonClient redissonClient = Redisson.create(config);
        return redissonClient;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("Initialize afterPropertiesSet");
    }

    @PostConstruct
    public void postConstruct() {
        log.info("Initialize postConstruct");
    }
}
