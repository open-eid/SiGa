package ee.openeid.siga.session.redis;

import ee.openeid.siga.session.configuration.RedisSessionProperties;
import ee.openeid.siga.session.spi.SessionLockRegistry;
import ee.openeid.siga.session.spi.SessionLockRegistryTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redis-backed contract tests for {@link SessionLockRegistry}. The shared SPI contract is
 * inherited from {@link SessionLockRegistryTest}; this subclass wires two independent
 * {@link RedisLockRegistry} instances over the same Testcontainers Redis, each exposed as a
 * {@code SessionLockRegistry} via the same {@code redisLockRegistry::obtain} lambda used by
 * {@code RedisSessionConfiguration}, so the contract is verified against the production bean
 * shape rather than against {@code RedisLockRegistry} directly.
 */
@Tag("docker")
@Testcontainers
class RedisSessionLockRegistryTest extends SessionLockRegistryTest {

    private static final String LOCK_REGISTRY_KEY = "siga:lock:test";
    private static final long LOCK_TTL_MS = 5_000L;

    @Container
    private static final GenericContainer<?> REDIS = RedisTestSupport.newRedisContainer();

    private static LettuceConnectionFactory factory;
    private static RedisLockRegistry primary;
    private static RedisLockRegistry secondary;
    private static SessionLockRegistry primaryRegistry;
    private static SessionLockRegistry secondaryRegistry;

    @BeforeAll
    static void initClients() {
        factory = RedisTestSupport.connectionFactory(REDIS);
        primary = new RedisLockRegistry(factory, LOCK_REGISTRY_KEY, LOCK_TTL_MS);
        secondary = new RedisLockRegistry(factory, LOCK_REGISTRY_KEY, LOCK_TTL_MS);
        primaryRegistry = primary::obtain;
        secondaryRegistry = secondary::obtain;
    }

    @AfterAll
    static void closeClients() {
        if (primary != null) primary.destroy();
        if (secondary != null) secondary.destroy();
        if (factory != null) factory.destroy();
    }

    @Override
    protected SessionLockRegistry primaryRegistry() {
        return primaryRegistry;
    }

    @Override
    protected SessionLockRegistry secondaryRegistry() {
        return secondaryRegistry;
    }

    @Test
    void shouldKeepLockExclusive_WhenHeldLongerThanInitialTtl() {
        // Pins the split-brain invariant for the per-signature/per-certificate locks used during
        // MID/SID long-poll status checks: while the owning JVM is alive, the lock must stay
        // exclusive even if the holder runs longer than the initial Redis TTL. Without renewal
        // the Redis key would expire, a peer registry could acquire the same key, and two cluster
        // nodes would process the same signature concurrently. Both registries here are built via
        // the production bean factory so behavior tracks RedisSessionConfiguration#redisLockRegistry
        // exactly — including PUB_SUB_LOCK, the {lock}-hashtagged key namespace, and the renewal
        // task scheduler.
        // see RedisSessionConfiguration.redisLockRenewalTaskScheduler() and SessionLocks.runAndUnlock()
        RedisSessionConfiguration config = new RedisSessionConfiguration();
        // Only lockTtl matters here (2s, so the renewal lease is observable within the test); the
        // remaining values are the binding defaults.
        RedisSessionProperties properties = new RedisSessionProperties(
                Duration.ofSeconds(300), Duration.ofSeconds(2), 100, 32, false);

        ThreadPoolTaskScheduler scheduler = (ThreadPoolTaskScheduler) config.redisLockRenewalTaskScheduler(properties);
        scheduler.initialize();
        RedisLockRegistry holder = config.redisLockRegistry(factory, properties, scheduler);
        RedisLockRegistry contender = config.redisLockRegistry(factory, properties, scheduler);
        try {
            Lock holderLock = holder.obtain("session-renewal");
            Lock contenderLock = contender.obtain("session-renewal");

            assertTrue(holderLock.tryLock(), "Holder must acquire the lock initially");
            try {
                Duration waitPastTtl = properties.lockTtl().plusSeconds(1);
                Awaitility.await()
                        .pollDelay(waitPastTtl)
                        .atMost(waitPastTtl.plusSeconds(2))
                        .until(() -> true);

                assertFalse(contenderLock.tryLock(),
                        "Contender acquired a lock the live holder still owns — "
                                + "Redis TTL elapsed without renewal (split-brain).");
            } finally {
                // Best-effort release: if the renewal contract ever regresses and the contender
                // takes ownership, Spring Integration throws ConcurrentModificationException on
                // the holder's unlock. That stems from the same root cause this test pins and
                // must not mask the primary assertion.
                try {
                    holderLock.unlock();
                } catch (RuntimeException ignored) {
                    // expected on the failure path
                }
            }
        } finally {
            holder.destroy();
            contender.destroy();
            scheduler.shutdown();
        }
    }
}
