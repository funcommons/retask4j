package fun.commons.retask4j.http.worker;


import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "retask4j.http")
public class FuHttpTaskWorkerProperties {

    private Map<String,Object> redis = new HashMap<>();
    @Valid
    private List<FuHttpTaskWorkerConfig> workers;

}