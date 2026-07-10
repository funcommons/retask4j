package fun.commons.retask4j.http.config;

public final class TopicValidator {

    private TopicValidator() {}

    public static void validate(String topic) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be null or blank");
        }
        if (topic.length() > 128) {
            throw new IllegalArgumentException("topic must not exceed 128 characters: " + topic.length());
        }
        if (topic.contains("{") || topic.contains("}") || topic.contains(":")) {
            throw new IllegalArgumentException("topic must not contain '{', '}' or ':' (conflicts with Redis key syntax): " + topic);
        }
        for (int i = 0; i < topic.length(); i++) {
            char c = topic.charAt(i);
            if (c < ' ' || c == 127) {
                throw new IllegalArgumentException("topic must not contain control characters");
            }
        }
    }
}
