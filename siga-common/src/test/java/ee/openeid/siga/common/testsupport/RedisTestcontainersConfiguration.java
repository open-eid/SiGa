package ee.openeid.siga.common.testsupport;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "siga.session-storage", name = "type",
        havingValue = "redis", matchIfMissing = true)
public class RedisTestcontainersConfiguration {

    // JVM-singleton: started once per Surefire fork, shared across every cached
    // Spring test context. No explicit stop — Testcontainers ships a sidecar
    // reaper container ("Ryuk") that holds a socket to this JVM and removes any
    // container, network, and volume tagged with this session's label as soon
    // as the JVM exits, so the singleton cannot leak between mvn invocations.
    // A per-context @Bean GenericContainer<?> would fork one Docker container,
    // one Lettuce pub/sub, one lock-renewal scheduler, and one @Scheduled
    // reprocessor per cached context — cumulative load makes the Smart-ID
    // polling's first 6 s-delayed poll miss its 25 s test window under a
    // full-suite run, so the singleton is mandatory.
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--notify-keyspace-events", "Ex");

    static {
        REDIS.start();
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return REDIS;
    }

    @Bean
    RedisKeyspaceCleaner redisKeyspaceCleaner() {
        return new RedisKeyspaceCleaner();
    }

    static final class RedisKeyspaceCleaner {

        // Runs before any @Scheduled reprocessor tick fires. FLUSHALL is issued
        // via the container's own redis-cli so siga-common does not need a
        // spring-data-redis dependency just for cross-context cleanup.
        @PostConstruct
        void flush() throws Exception {
            Container.ExecResult result = REDIS.execInContainer("redis-cli", "flushall");
            if (result.getExitCode() != 0) {
                throw new IllegalStateException(
                        "Redis FLUSHALL failed (exit=" + result.getExitCode() + "): "
                                + result.getStderr());
            }
        }
    }
}
