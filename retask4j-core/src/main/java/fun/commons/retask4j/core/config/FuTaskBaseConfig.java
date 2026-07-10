package fun.commons.retask4j.core.config;


import lombok.Getter;

@Getter
public class FuTaskBaseConfig {

    // Maximum TTL for any time-based field (30 days)
    public static final int MAX_EXPIRE_SECONDS = 2_592_000;

    private final String topic;

    public FuTaskBaseConfig() {
        this.topic = "default";
    }

    public FuTaskBaseConfig(String topic ) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be null or blank");
        }
        if (topic.length() > 128) {
            throw new IllegalArgumentException("topic must not exceed 128 characters: " + topic.length());
        }
        if (topic.contains("{") || topic.contains("}")) {
            throw new IllegalArgumentException("topic must not contain '{' or '}' (conflicts with Redis hash tag syntax): " + topic);
        }
        if (topic.contains(":")) {
            throw new IllegalArgumentException("topic must not contain ':' (conflicts with Redis key namespace convention): " + topic);
        }
        for (int i = 0; i < topic.length(); i++) {
            char c = topic.charAt(i);
            if (c < ' ' || c == '') {
                throw new IllegalArgumentException("topic must not contain control characters: " + topic);
            }
        }
        this.topic = topic;
    }

}
