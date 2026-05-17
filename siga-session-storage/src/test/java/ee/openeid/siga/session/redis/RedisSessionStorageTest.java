package ee.openeid.siga.session.redis;

import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.session.spi.SessionStorage;
import ee.openeid.siga.session.spi.SessionStorageTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redis-backed contract tests plus backend-specific TTL assertions. The shared SPI contract is
 * inherited from {@link SessionStorageTest}; the TTL tests use Redis's
 * {@code EXPIRE} / {@code TTL} commands directly to manipulate and observe per-key TTL without
 * relying on wall-clock sleeps.
 */
@Tag("docker")
@Testcontainers
class RedisSessionStorageTest extends SessionStorageTest {

    @Container
    private static final GenericContainer<?> REDIS = RedisTestSupport.newRedisContainer();

    private static LettuceConnectionFactory factory;
    private static RedisTemplate<String, Object> template;

    private RedisSessionStorage storage;

    @BeforeAll
    static void initClients() {
        factory = RedisTestSupport.connectionFactory(REDIS);
        template = RedisTestSupport.sessionTemplate(factory);
    }

    @AfterAll
    static void closeClients() {
        if (factory != null) factory.destroy();
    }

    @Override
    protected SessionStorage storage() {
        return storage;
    }

    @Override
    protected void resetStorage() {
        RedisTestSupport.flushAll(factory);
        storage = new RedisSessionStorage(template, Duration.ofMinutes(5));
    }

    @Test
    void shouldSetTtl_WhenUpdateCalled() {
        Duration ttl = Duration.ofSeconds(60);
        storage = new RedisSessionStorage(template, ttl);
        Session session = newSession("v1_svc_fresh_ttl");
        storage.update(session);

        String sessionKey = RedisSessionKeys.session(session.getSessionId());
        Long ttlAfterUpdate = template.getExpire(sessionKey, TimeUnit.MILLISECONDS);
        assertNotNull(ttlAfterUpdate);
        assertTrue(ttlAfterUpdate > 55_000 && ttlAfterUpdate <= 60_000,
                "update() should set TTL near the configured idle window (~60s), was " + ttlAfterUpdate);
    }

    @Test
    void shouldRefreshTtl_WhenGetCalled() {
        Duration ttl = Duration.ofSeconds(60);
        storage = new RedisSessionStorage(template, ttl);
        Session session = newSession("v1_svc_ttl");
        storage.update(session);

        String sessionKey = RedisSessionKeys.session(session.getSessionId());
        // Shorten the TTL to simulate elapsed time without a sleep — keeps the test deterministic.
        template.expire(sessionKey, Duration.ofSeconds(10));
        Long ttlBeforeGet = template.getExpire(sessionKey, TimeUnit.MILLISECONDS);

        Optional<Session> loaded = storage.get(session.getSessionId());
        assertTrue(loaded.isPresent());

        Long ttlAfterGet = template.getExpire(sessionKey, TimeUnit.MILLISECONDS);
        assertNotNull(ttlBeforeGet);
        assertNotNull(ttlAfterGet);
        assertTrue(ttlBeforeGet <= 10_500,
                "Pre-condition: TTL should be the manually-shortened value, was " + ttlBeforeGet);
        assertTrue(ttlAfterGet > 30_000,
                "get() should refresh session TTL via GETEX to ~sessionTtl=60s, was " + ttlAfterGet);
    }

    @Test
    void shouldNotRefreshTtl_WhenPeekCalled() {
        Duration ttl = Duration.ofSeconds(60);
        storage = new RedisSessionStorage(template, ttl);
        Session session = newSession("v1_svc_peek_ttl");
        storage.update(session);

        String sessionKey = RedisSessionKeys.session(session.getSessionId());
        template.expire(sessionKey, Duration.ofSeconds(10));
        Long ttlBeforePeek = template.getExpire(sessionKey, TimeUnit.MILLISECONDS);

        Optional<Session> loaded = storage.peek(session.getSessionId());
        assertTrue(loaded.isPresent());
        assertEquals(session.getSessionId(), loaded.get().getSessionId());

        Long ttlAfterPeek = template.getExpire(sessionKey, TimeUnit.MILLISECONDS);
        assertNotNull(ttlBeforePeek);
        assertNotNull(ttlAfterPeek);
        assertTrue(ttlAfterPeek <= ttlBeforePeek,
                "peek() must not refresh TTL — was " + ttlBeforePeek + " ms before, " + ttlAfterPeek + " ms after");
    }

    @Test
    void shouldCountOnlySessionKeys_WhenSizeCalledWithUnrelatedKeysPresent() {
        // RedisSessionStorage.size() (per its javadoc) uses SCAN with the SESSION_SCAN_PATTERN
        // (siga:session:*) so unrelated siga:* keys (locks at siga:lock:{lock} per
        // RedisSessionConfiguration.LOCK_REGISTRY_KEY, due queues at siga:{reprocess}:*) are excluded
        // from the operational session count. The pattern filter is the single point keeping the
        // count clean; SessionStorageTest.shouldReportCount_WhenSizeCalled exercises the happy path
        // but does not seed unrelated keys, so a refactor that broadens the pattern (e.g. to siga:*)
        // would pass that test silently. Pin the filter explicitly.
        storage = new RedisSessionStorage(template, Duration.ofMinutes(5));
        assertEquals(0L, storage.size(), "size() must be 0 on a freshly flushed cache");

        storage.update(newSession("v1_svc_size_1"));
        storage.update(newSession("v1_svc_size_2"));
        storage.update(newSession("v1_svc_size_3"));

        // Seed keys that share the siga: namespace but are NOT session keys. SCAN must skip these.
        template.opsForValue().set("siga:lock:foo", "x");
        template.opsForValue().set(RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, "x");

        assertEquals(3L, storage.size(),
                "size() must count only siga:session:* keys, ignoring locks and due queues");
    }

    @Test
    void shouldStoreEmptyMaps_WhenUpdateCalledWithNullSidecarMaps() {
        // RedisSessionStorage.update() must normalize null sidecar maps before serializing the
        // aggregate value. Callers downstream rely on getSignatureSessions()/getCertificateSessions()
        // never being null after a Redis round-trip.
        storage = new RedisSessionStorage(template, Duration.ofMinutes(5));
        Session session = HashcodeContainerSession.builder()
                .sessionId("v1_svc_null_sidecars")
                .clientName("c").serviceName("s").serviceUuid("u")
                .dataFiles(Collections.emptyList())
                .signatures(Collections.emptyList())
                .build();
        session.setSignatureSessions(null);
        session.setCertificateSessions(null);

        storage.update(session);

        String key = RedisSessionKeys.session(session.getSessionId());
        assertNotNull(template.opsForValue().get(key),
                "The aggregate session value must be present even when caller passed null maps");

        Optional<Session> loaded = storage.peek(session.getSessionId());
        assertTrue(loaded.isPresent());
        assertNotNull(loaded.get().getSignatureSessions(),
                "peek() must return non-null signatureSessions per the Session contract");
        assertNotNull(loaded.get().getCertificateSessions(),
                "peek() must return non-null certificateSessions per the Session contract");
        assertTrue(loaded.get().getSignatureSessions().isEmpty());
        assertTrue(loaded.get().getCertificateSessions().isEmpty());
    }

    @Test
    void shouldNotThrowAndRemainAbsent_WhenRemoveCalledForUnknownSessionId() {
        // RedisSessionStorage.remove() is a single redisTemplate.delete() call, which
        // silently no-ops if the key is absent (Redis DEL returns 0). The SPI base contract test
        // SessionStorageTest.shouldNotThrow_WhenRemoveCalledForMissingContainer pins the no-throw
        // contract; this test additionally pins the post-condition that no ghost session is left
        // behind. Guards against a future refactor swapping delete() for a check-then-act path
        // (e.g. if (hasKey) delete(...)) that could race or write a sentinel before deletion.
        storage = new RedisSessionStorage(template, Duration.ofMinutes(5));

        assertDoesNotThrow(() -> storage.remove("v1_svc_never_existed_here"));
        assertTrue(storage.peek("v1_svc_never_existed_here").isEmpty(),
                "peek must remain empty after remove() of an unknown sessionId");
    }
}
