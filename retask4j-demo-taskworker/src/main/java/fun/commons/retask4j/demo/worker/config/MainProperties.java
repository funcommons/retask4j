package fun.commons.retask4j.demo.worker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@Data
@ConfigurationProperties(prefix = "demo")
public class MainProperties {
    private Map<String, Object> datasource;
    private Map<String, Object> redis;
}