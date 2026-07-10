package fun.commons.retask4j.http;

import java.util.Set;

public final class HttpTaskHeaders {

    public static final String PREFIX = "retask4j-";

    public static final String RETRY_PLAN = PREFIX + "retry-plan";
    public static final String TASK_TIMING = PREFIX + "task-timing";
    public static final String TASK_DELAY = PREFIX + "task-delay";
    public static final String ASSERT_RESPONSE = PREFIX + "assert-response";
    public static final String CALLBACK_URL = PREFIX + "callback-url";

    public static final Set<String> HTTP_SCHEMES = Set.of("http", "https");

    private HttpTaskHeaders() {}
}
