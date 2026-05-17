package ee.openeid.siga.session.redis;

import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.session.SessionStatus;
import ee.openeid.siga.common.session.SignatureSession;
import ee.openeid.siga.session.configuration.SessionStatusReprocessingProperties;
import ee.openeid.siga.session.spi.SessionLocks;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

import static ee.openeid.siga.common.model.SigningType.SMART_ID;
import static ee.openeid.siga.common.session.ProcessingStatus.PROCESSING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies reliability behavior of the Redis-backed session storage.
 *
 * <p>The tests cover TTL repair for sessions, lock TTL configuration validation, and lost lock
 * ownership handling.
 */
@Tag("docker")
@Testcontainers
class RedisSessionReliabilityTest {

    @Container
    private static final GenericContainer<?> REDIS = RedisTestSupport.newRedisContainer();

    private static LettuceConnectionFactory factory;
    private static RedisTemplate<String, Object> sessionTemplate;
    private static StringRedisTemplate stringTemplate;

    private RedisSessionStorage storage;
    private RedisSessionStatusScanner scanner;

    @BeforeAll
    static void initClients() {
        factory = RedisTestSupport.connectionFactory(REDIS);
        sessionTemplate = RedisTestSupport.sessionTemplate(factory);
        stringTemplate = RedisTestSupport.stringTemplate(factory);
    }

    @AfterAll
    static void closeClients() {
        if (factory != null) factory.destroy();
    }

    @BeforeEach
    void resetState() {
        RedisTestSupport.flushAll(factory);
        storage = new RedisSessionStorage(sessionTemplate, Duration.ofMinutes(5));
        scanner = new RedisSessionStatusScanner(stringTemplate, storage,
                SessionStatusReprocessingProperties.withDefaults(), 10);
    }

    // -------------------------------------------------------------------------
    // A session value can exist without an expiry if Redis is changed outside the normal write
    // path. The next read must add the configured TTL. The same must happen when a due-queue scan
    // reads the session without extending healthy idle sessions.
    // -------------------------------------------------------------------------
    @Test
    void shouldHealOrphanValue_WhenPeekFindsMissingTtl() {
        String sessionId = "v1_svc_orphan_no_ttl";
        String key = RedisSessionKeys.session(sessionId);

        // Create a readable session value, then remove its expiry to mimic an out-of-band write.
        storage.update(newSession(sessionId));
        sessionTemplate.persist(key);

        Long ttlAfterPlant = sessionTemplate.getExpire(key, TimeUnit.SECONDS);
        assertEquals(-1L, ttlAfterPlant,
                "Setup check: orphan value has no TTL (Redis returns -1 for keys without expiry)");

        // A read that does not refresh healthy sessions still repairs this missing-TTL state.
        Optional<Session> peeked = storage.peek(sessionId);
        assertTrue(peeked.isPresent(), "The orphan value must still be readable as a session");
        Long ttlAfterPeek = sessionTemplate.getExpire(key, TimeUnit.SECONDS);
        assertNotNull(ttlAfterPeek);
        assertTrue(ttlAfterPeek > 0L && ttlAfterPeek <= Duration.ofMinutes(5).getSeconds(),
                "Reading the orphan must apply the configured session TTL. "
                        + "Observed TTL after peek: " + ttlAfterPeek);

        // The healed session is still a valid entry in the store, so the counter includes it.
        assertEquals(1L, storage.size(),
                "The size counter still counts the healed session because the value remains valid.");

        // Remove the expiry again and verify that scanning a due entry repairs it too.
        SignatureSession overdue = SignatureSession.builder()
                .signingType(SMART_ID)
                .sessionStatus(SessionStatus.builder()
                        .processingStatus(PROCESSING)
                        .processingStatusTimestamp(Instant.now().minus(Duration.ofMinutes(10)))
                        .build())
                .build();
        Session overdueSession = newSession(sessionId);
        overdueSession.setSignatureSessions(Map.of("sig-1", overdue));
        storage.update(overdueSession);
        sessionTemplate.persist(key);
        assertEquals(-1L, sessionTemplate.getExpire(key, TimeUnit.SECONDS),
                "Setup check reset: TTL is back to -1 after removing the expiry.");

        stringTemplate.opsForZSet().add(
                RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, sessionId, 1L);

        List<String> emitted = new ArrayList<>();
        scanner.scanSignatureSessions(
                new ee.openeid.siga.session.spi.StatusReprocessingFilter(
                        Long.MAX_VALUE, Instant.now(), Instant.now()),
                emitted::add);

        Long ttlAfterScan = sessionTemplate.getExpire(key, TimeUnit.SECONDS);
        assertNotNull(ttlAfterScan);
        assertTrue(ttlAfterScan > 0L && ttlAfterScan <= Duration.ofMinutes(5).getSeconds(),
                "Scanning a due entry must also heal the missing TTL. Observed TTL after scan: "
                        + ttlAfterScan);
    }

    // Cross-key Lua atomicity is exercised separately with real TCP truncation.

    // -------------------------------------------------------------------------
    // A lock TTL must stay below the session TTL, otherwise a stale lock can outlive the session
    // payload it protects.
    // -------------------------------------------------------------------------
    @Test
    void shouldRejectLockTtlAboveSessionTtl_AtBindingTime() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestFactoryConfig.class, RedisSessionConfiguration.class)
                .withPropertyValues(
                        "siga.session-storage.application-cache-version=v1",
                        "siga.session-storage.type=redis",
                        "siga.session-storage.redis.session-ttl=PT10S",
                        "siga.session-storage.redis.lock-ttl=PT60S"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable startupFailure = context.getStartupFailure();
                    assertNotNull(startupFailure,
                            "Context must record a startup failure when lockTtl > sessionTtl");
                    String message = collectMessages(startupFailure);
                    assertTrue(message.contains("lockTtl must be strictly less than sessionTtl"),
                            "Startup failure must include the cross-field validation message. "
                                    + "Actual chained messages: " + message);
                });
    }

    // -------------------------------------------------------------------------
    // When Redis cluster mode is active (spring.data.redis.cluster.nodes set), the three
    // spring.data.redis.lettuce.cluster.refresh.* properties must be configured. Otherwise
    // Lettuce's defaults leave periodic refresh OFF and adaptive triggers EMPTY, no
    // ClusterTopologyChangedEvent fires on failover, and RedisSessionExpiryNotifier's pub/sub
    // subscription never follows a promoted master. RedisClusterTopologyRefreshValidation
    // catches that at property-binding time so misconfig surfaces at startup, not after a
    // production failover.
    // -------------------------------------------------------------------------
    @Test
    void shouldRejectClusterModeWithoutTopologyRefresh_AtBindingTime() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestFactoryConfig.class, RedisSessionConfiguration.class)
                .withPropertyValues(
                        "siga.session-storage.application-cache-version=v1",
                        "siga.session-storage.type=redis",
                        // Cluster mode triggers the validator; the seed addresses are never actually
                        // dialed because TestFactoryConfig supplies its own RedisConnectionFactory
                        // and Spring Boot's @ConditionalOnMissingBean suppresses the auto-config.
                        "spring.data.redis.cluster.nodes=siga-redis-1:6379"
                        // All three refresh properties intentionally absent.
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable startupFailure = context.getStartupFailure();
                    assertNotNull(startupFailure,
                            "Context must record a startup failure when cluster topology refresh is missing");
                    String message = collectMessages(startupFailure);
                    assertTrue(message.contains("spring.data.redis.lettuce.cluster.refresh.period"),
                            "Startup failure must name the missing period property. "
                                    + "Actual chained messages: " + message);
                    assertTrue(message.contains("spring.data.redis.lettuce.cluster.refresh.adaptive"),
                            "Startup failure must name the missing adaptive property. "
                                    + "Actual chained messages: " + message);
                    assertTrue(message.contains("spring.data.redis.lettuce.cluster.refresh.dynamic-refresh-sources"),
                            "Startup failure must name the missing dynamic-refresh-sources property. "
                                    + "Actual chained messages: " + message);
                });
    }

    @Test
    void shouldRejectClusterMissingAdaptive_AtBindingTime() {
        // Per-property granularity: period and dynamic-refresh-sources are set, adaptive isn't.
        // Only the adaptive @AssertTrue must fire; the other two must NOT trip.
        new ApplicationContextRunner()
                .withUserConfiguration(TestFactoryConfig.class, RedisSessionConfiguration.class)
                .withPropertyValues(
                        "siga.session-storage.application-cache-version=v1",
                        "siga.session-storage.type=redis",
                        "spring.data.redis.cluster.nodes=siga-redis-1:6379",
                        "spring.data.redis.lettuce.cluster.refresh.period=30s",
                        "spring.data.redis.lettuce.cluster.refresh.dynamic-refresh-sources=true"
                        // adaptive intentionally absent (defaults to false).
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    String message = collectMessages(context.getStartupFailure());
                    assertTrue(message.contains("spring.data.redis.lettuce.cluster.refresh.adaptive must be true"),
                            "Startup failure must pinpoint the adaptive property. "
                                    + "Actual chained messages: " + message);
                    assertFalse(message.contains("spring.data.redis.lettuce.cluster.refresh.period must be set"),
                            "Period violation must not fire when period is configured. "
                                    + "Actual chained messages: " + message);
                });
    }

    @Test
    void shouldRejectSubSecondTopologyRefreshPeriod_AtBindingTime() {
        // A sub-second period would hammer every cluster node with CLUSTER NODES requests for
        // no functional gain. Pin the floor at 1s so a typo like PT0.5S surfaces at startup
        // rather than producing a self-inflicted load problem in production.
        new ApplicationContextRunner()
                .withUserConfiguration(TestFactoryConfig.class, RedisSessionConfiguration.class)
                .withPropertyValues(
                        "siga.session-storage.application-cache-version=v1",
                        "siga.session-storage.type=redis",
                        "spring.data.redis.cluster.nodes=siga-redis-1:6379",
                        "spring.data.redis.lettuce.cluster.refresh.period=PT0.5S",
                        "spring.data.redis.lettuce.cluster.refresh.adaptive=true",
                        "spring.data.redis.lettuce.cluster.refresh.dynamic-refresh-sources=true"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    String message = collectMessages(context.getStartupFailure());
                    assertTrue(message.contains("spring.data.redis.lettuce.cluster.refresh.period must be at least 1s"),
                            "Startup failure must pinpoint the period floor violation. "
                                    + "Actual chained messages: " + message);
                    assertFalse(message.contains("must be set"),
                            "Missing-period violation must NOT fire when a (too-short) period is configured. "
                                    + "Actual chained messages: " + message);
                });
    }

    @Test
    void shouldStartUp_WhenClusterTopologyRefreshFullyConfigured() {
        // Positive coverage: with cluster.nodes set AND all three refresh properties configured,
        // the validator passes and the context starts. TestFactoryConfig satisfies the connection
        // factory so Spring's auto-config never tries to dial the seed addresses.
        new ApplicationContextRunner()
                .withUserConfiguration(TestFactoryConfig.class, RedisSessionConfiguration.class)
                .withPropertyValues(
                        "siga.session-storage.application-cache-version=v1",
                        "siga.session-storage.type=redis",
                        "spring.data.redis.cluster.nodes=siga-redis-1:6379",
                        "spring.data.redis.lettuce.cluster.refresh.period=30s",
                        "spring.data.redis.lettuce.cluster.refresh.adaptive=true",
                        "spring.data.redis.lettuce.cluster.refresh.dynamic-refresh-sources=true"
                )
                .run(context -> assertThat(context).hasNotFailed());
    }

    // -------------------------------------------------------------------------
    // Simulates one node losing a Redis lock while work is still running. Another registry
    // acquires the same lock, then the original holder observes the owner mismatch during unlock.
    // The caller must see a false return value.
    // -------------------------------------------------------------------------
    @Test
    void shouldSignalLostOwnership_ByReturningFalse_WhenLockLeaseLapsesMidWork() {
        String registryKey = "siga:lock:{lock}";
        RedisLockRegistry holder = new RedisLockRegistry(factory, registryKey, 5_000L);
        RedisLockRegistry contender = new RedisLockRegistry(factory, registryKey, 5_000L);
        try {
            holder.setRedisLockType(RedisLockRegistry.RedisLockType.PUB_SUB_LOCK);
            contender.setRedisLockType(RedisLockRegistry.RedisLockType.PUB_SUB_LOCK);

            // Record whether unlock reported the owner mismatch. This confirms the false return
            // came from lost ownership, not from a failed acquisition.
            boolean[] cmeThrownFromUnlock = {false};
            Lock instrumented = new InstrumentedLock(
                    holder.obtain("split-brain-key"), cmeThrownFromUnlock);

            AtomicBoolean holderWorkRan = new AtomicBoolean();
            boolean tryRunResult = SessionLocks.tryRun(instrumented, () -> {
                // Simulate partition plus peer takeover: while the holder runs, the Redis-side
                // key disappears and another registry acquires the same lock.
                stringTemplate.delete(registryKey + ":split-brain-key");
                Lock contenderLock = contender.obtain("split-brain-key");
                assertTrue(contenderLock.tryLock(),
                        "Setup check: a peer registry must be able to acquire a lock the first "
                                + "holder believes it owns. Without this, lost ownership is not "
                                + "being exercised.");
                contenderLock.unlock();
                holderWorkRan.set(true);
                // Returning from this callback calls unlock, where the owner mismatch is detected.
            });

            assertTrue(holderWorkRan.get(),
                    "Setup check: the work inside the lock must have executed before the CME "
                            + "surfaced (work runs first, unlock fires in finally).");
            assertTrue(cmeThrownFromUnlock[0],
                    "Setup check: Spring Integration's Lock.unlock() must have thrown CME "
                            + "(owner UUID mismatch after the Redis-side key was deleted and "
                            + "the contender ran). Without this throw, the lost-ownership path "
                            + "is not exercised and the assertion below would not be probative.");
            assertFalse(tryRunResult,
                    "The lock helper must return false when unlock reports lost ownership. "
                            + "Returning true here would conflate exclusive ownership with "
                            + "concurrent processing of the same session.");
        } finally {
            holder.destroy();
            contender.destroy();
        }
    }

    /**
     * Wraps a {@link Lock} to record whether {@code unlock()} threw
     * {@link ConcurrentModificationException}. This confirms the lost-ownership path is being
     * exercised before the return value is asserted.
     */
    private record InstrumentedLock(Lock delegate, boolean[] cmeThrown) implements Lock {
        @Override
        public void lock() {
            delegate.lock();
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            delegate.lockInterruptibly();
        }

        @Override
        public boolean tryLock() {
            return delegate.tryLock();
        }

        @Override
        public boolean tryLock(long time, java.util.concurrent.TimeUnit unit) throws InterruptedException {
            return delegate.tryLock(time, unit);
        }

        @Override
        public java.util.concurrent.locks.Condition newCondition() {
            return delegate.newCondition();
        }

        @Override
        public void unlock() {
            try {
                delegate.unlock();
            } catch (ConcurrentModificationException cme) {
                cmeThrown[0] = true;
                throw cme;
            }
        }
    }

    @Configuration
    static class TestFactoryConfig {
        @Bean(destroyMethod = "destroy")
        public RedisConnectionFactory redisConnectionFactory() {
            return RedisTestSupport.connectionFactory(REDIS);
        }

        @Bean
        public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
            return RedisTestSupport.stringTemplate((LettuceConnectionFactory) factory);
        }
    }

    private static Session newSession(String sessionId) {
        return HashcodeContainerSession.builder()
                .sessionId(sessionId)
                .clientName("client")
                .serviceName("svc")
                .serviceUuid("uuid")
                .dataFiles(Collections.emptyList())
                .signatures(Collections.emptyList())
                .build();
    }

    private static String collectMessages(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            messages.append(t.getClass().getSimpleName()).append(": ")
                    .append(t.getMessage()).append(" | ");
        }
        return messages.toString();
    }
}
