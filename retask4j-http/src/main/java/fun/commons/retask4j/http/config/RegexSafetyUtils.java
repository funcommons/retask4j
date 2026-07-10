package fun.commons.retask4j.http.config;

import java.util.regex.Pattern;

public final class RegexSafetyUtils {

    private RegexSafetyUtils() {}

    public static final int MAX_REGEX_LENGTH = 256;

    static final Pattern REDOS_PATTERN =
        Pattern.compile("\\([^)]*[+*][^)]*\\)[+*]|\\([^)]*\\)[+*]\\{|\\([^)]*\\|[^)]*\\)[+*]");

    public static Pattern compileSafePattern(String regex) {
        if (regex == null) {
            throw new NullPointerException("regex pattern must not be null");
        }
        if (regex.length() > MAX_REGEX_LENGTH) {
            throw new IllegalArgumentException("Regex pattern exceeds maximum length of " + MAX_REGEX_LENGTH + " characters");
        }
        if (REDOS_PATTERN.matcher(regex).find()) {
            throw new IllegalArgumentException("Regex pattern contains nested quantifiers that may cause catastrophic backtracking: " + regex);
        }
        return Pattern.compile(regex);
    }
}
