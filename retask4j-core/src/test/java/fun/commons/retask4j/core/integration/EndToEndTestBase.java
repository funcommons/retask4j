package fun.commons.retask4j.core.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

/**
 * Base class for end-to-end integration tests that exercise the full caller/worker stack
 * against a real Redis instance.
 *
 * <p>Tests are skipped unless {@code -Dredis.host=...} is set (and optionally
 * {@code -Dredis.port=...}, default 6379). This allows CI to run them only when
 * a Redis instance is provisioned.
 *
 * <p>Example:
 * <pre>
 * mvn test -pl retask4j-core -Dtest=EndToEndNormalModeTest -Dredis.host=localhost
 * </pre>
 */
@EnabledIfSystemProperty(named = "redis.host", matches = ".+")
public abstract class EndToEndTestBase {

    protected static RedissonClient redisson;
    protected static int redisPort;
    protected static String redisHost;

    @BeforeAll
    static void setupRedis() {
        redisHost = System.getProperty("redis.host", "localhost");
        redisPort = Integer.parseInt(System.getProperty("redis.port", "6379"));
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort)
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(4);
        redisson = Redisson.create(config);
    }

    @AfterAll
    static void teardownRedis() {
        if (redisson != null) {
            redisson.shutdown();
        }
    }
}
