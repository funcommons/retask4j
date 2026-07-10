package fun.commons.retask4j.http.caller;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "retask4j.http")
public class FuHttpTaskCallerProperties {
    private Map<String,Object> redis = new HashMap<>();
    @Valid
    private List<FuHttpTaskCallerConfig> callers = new ArrayList<>();
}