package fun.commons.retask4j.http.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "retask4j.http")
public class RedissonConfigProperties {
    private Map<String, Object> redis = new HashMap<>();
}
