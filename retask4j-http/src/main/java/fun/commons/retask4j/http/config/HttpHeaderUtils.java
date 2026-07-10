package fun.commons.retask4j.http.config;

public final class HttpHeaderUtils {

    private HttpHeaderUtils() {}

    public static void validateNoCrlf(String name, String value) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Header name must not be null or blank");
        }
        if (name.contains("\r") || name.contains("\n")) {
            throw new IllegalArgumentException("Header name must not contain CR/LF: " + name);
        }
        if (value != null && (value.contains("\r") || value.contains("\n"))) {
            throw new IllegalArgumentException("Header value must not contain CR/LF for header: " + name);
        }
    }
}
