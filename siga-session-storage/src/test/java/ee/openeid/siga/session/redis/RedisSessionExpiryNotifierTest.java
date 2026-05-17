package ee.openeid.siga.session.redis;

import ee.openeid.siga.session.spi.ContainerExpiredEvent;
import io.lettuce.core.AbstractRedisClient;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@link RedisSessionExpiryNotifier} is required: it bridges Redis keyspace expiry
 * notifications into {@link ContainerExpiredEvent}s and — equally importantly — fails fast at
 * startup if {@code notify-keyspace-events} is misconfigured. Without the bridge, expired session
 * keys vanish without triggering auth connection cleanup or Redis due-queue cleanup. Without the
 * verification, that exact misconfiguration ships silently to production.
 *
 * <p>Each scenario spins its own Valkey container with a custom {@code --notify-keyspace-events}
 * command so the fail-fast guard can be exercised directly. The shared
 * {@code RedisTestSupport.newRedisContainer()} bakes in {@code Ex} and so isn't suitable here.
 */
@Tag("docker")
class RedisSessionExpiryNotifierTest {

    private static final DockerImageName VALKEY_IMAGE = DockerImageName.parse("valkey/valkey:7.2.6-alpine");
    private static final int REDIS_PORT = 6379;

    private GenericContainer<?> redis;
    private LettuceConnectionFactory factory;
    private RedisSessionExpiryNotifier notifier;

    @AfterEach
    void tearDown() {
        if (notifier != null) {
            notifier.unsubscribe();
            notifier = null;
        }
        if (factory != null) {
            factory.destroy();
            factory = null;
        }
        if (redis != null) {
            redis.stop();
            redis = null;
        }
    }

    @Test
    void shouldFailFast_WhenNotifyKeyspaceEventsDisabled() {
        // No flags at all → Redis default is empty → verifier must throw.
        startRedis("");
        notifier = new RedisSessionExpiryNotifier(factory, recordingPublisher(new CopyOnWriteArrayList<>()), false);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, notifier::subscribe);
        assertTrue(thrown.getMessage().contains("notify-keyspace-events"),
                "Error must mention the config flag operators need to fix");
        assertTrue(thrown.getMessage().contains("Refusing to start"),
                "Error must make clear this is a fail-fast guard");
    }

    @Test
    void shouldFailFast_WhenExpiredFlagMissing() {
        // 'AK' enables every event EXCEPT 'x'/'A'. Real-world misconfiguration: an operator
        // tightened the parameter group with an incomplete flag set.
        startRedis("AK");
        notifier = new RedisSessionExpiryNotifier(factory, recordingPublisher(new CopyOnWriteArrayList<>()), false);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, notifier::subscribe);
        assertTrue(thrown.getMessage().contains("notify-keyspace-events"));
    }

    @Test
    void shouldStartUp_WhenSkipVerificationEnabledEvenWithBadConfig() {
        // Managed Valkey services (ElastiCache, MemoryDB) block CONFIG GET; the bypass lets
        // operators take responsibility for the parameter group themselves. Pin that the bypass
        // actually bypasses verification and doesn't throw.
        startRedis("");
        notifier = new RedisSessionExpiryNotifier(factory, recordingPublisher(new CopyOnWriteArrayList<>()), true);

        assertDoesNotThrow(notifier::subscribe);
    }

    @Test
    void shouldPublishContainerExpiredEvent_WhenSessionKeyExpires() {
        startRedis("Ex");
        List<Object> events = new CopyOnWriteArrayList<>();
        notifier = new RedisSessionExpiryNotifier(factory, recordingPublisher(events), false);
        notifier.subscribe();

        // Write a session key directly, then shorten its TTL so the keyspace event fires fast.
        StringRedisTemplate template = RedisTestSupport.stringTemplate(factory);
        template.opsForValue().set(RedisSessionKeys.session("v1_svc_notifier_x"), "payload");
        template.expire(RedisSessionKeys.session("v1_svc_notifier_x"), Duration.ofMillis(300));

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> events.stream().anyMatch(ContainerExpiredEvent.class::isInstance));

        Object first = events.stream()
                .filter(ContainerExpiredEvent.class::isInstance)
                .findFirst()
                .orElse(null);
        assertNotNull(first);
        assertEquals("v1_svc_notifier_x", ((ContainerExpiredEvent) first).sessionId());
    }

    @Test
    void shouldStopDeliveringEvents_AfterUnsubscribeCalled() {
        // PreDestroy contract: unsubscribe() must close the pub/sub connection so the JVM can
        // shut down cleanly and no further events leak into a partially-disposed event publisher.
        startRedis("Ex");
        List<Object> events = new CopyOnWriteArrayList<>();
        notifier = new RedisSessionExpiryNotifier(factory, recordingPublisher(events), false);
        notifier.subscribe();

        // Force an expiry to confirm the listener is live before teardown.
        StringRedisTemplate template = RedisTestSupport.stringTemplate(factory);
        template.opsForValue().set(RedisSessionKeys.session("v1_svc_pre_teardown"), "payload");
        template.expire(RedisSessionKeys.session("v1_svc_pre_teardown"), Duration.ofMillis(300));
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> events.stream().anyMatch(ContainerExpiredEvent.class::isInstance));

        notifier.unsubscribe();
        events.clear();

        // After teardown, fresh expiries must NOT produce events.
        template.opsForValue().set(RedisSessionKeys.session("v1_svc_post_teardown"), "payload");
        template.expire(RedisSessionKeys.session("v1_svc_post_teardown"), Duration.ofMillis(300));
        Awaitility.await()
                .pollDelay(1, TimeUnit.SECONDS)
                .atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(events.isEmpty(),
                        "No events must be delivered after unsubscribe(); leaked events: " + events));
    }

    @Test
    void shouldBeUnsubscribeIdempotent_WhenCalledTwice() {
        // Spring's @PreDestroy contract: unsubscribe must tolerate being called before subscribe()
        // (e.g. if the bean failed to wire) and again on shutdown without throwing NPE.
        startRedis("Ex");
        notifier = new RedisSessionExpiryNotifier(factory, recordingPublisher(new CopyOnWriteArrayList<>()), false);

        assertDoesNotThrow(notifier::unsubscribe);
        notifier.subscribe();
        assertDoesNotThrow(notifier::unsubscribe);
        assertDoesNotThrow(notifier::unsubscribe);
    }

    @Test
    void shouldFailFast_WhenConnectionFactoryIsNotLettuce() {
        // The notifier is wired with a RedisConnectionFactory; the production codepath assumes
        // Lettuce. If a future change wires Jedis (or a custom factory) the notifier must fail fast:
        // otherwise the application starts while container expiry cleanup is silently disabled.
        RedisConnectionFactory nonLettuce = nonLettuceFactory();
        RedisSessionExpiryNotifier guarded = new RedisSessionExpiryNotifier(
                nonLettuce, recordingPublisher(new CopyOnWriteArrayList<>()), false);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, guarded::subscribe);
        assertTrue(thrown.getMessage().contains("LettuceConnectionFactory"));
        assertTrue(thrown.getMessage().contains("Refusing to start"));
        // unsubscribe is a no-op when no connection was opened — must also not throw.
        assertDoesNotThrow(guarded::unsubscribe);
    }

    @Test
    void shouldFailFast_WhenLettuceNativeClientIsUnsupported() {
        LettuceConnectionFactory unsupported = new LettuceConnectionFactory() {
            @Override
            public AbstractRedisClient getNativeClient() {
                return null;
            }
        };
        RedisSessionExpiryNotifier guarded = new RedisSessionExpiryNotifier(
                unsupported, recordingPublisher(new CopyOnWriteArrayList<>()), true);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, guarded::subscribe);
        assertTrue(thrown.getMessage().contains("Unsupported Lettuce native client type"));
        assertDoesNotThrow(guarded::unsubscribe);
    }

    @Test
    void shouldIgnoreNonSessionExpirations() {
        // Pub/sub fires for every expired key; only siga:session:* should produce events.
        startRedis("Ex");
        AtomicReference<String> capturedId = new AtomicReference<>();
        notifier = new RedisSessionExpiryNotifier(factory, event -> {
            if (event instanceof ContainerExpiredEvent expired) {
                capturedId.set(expired.sessionId());
            }
        }, false);
        notifier.subscribe();

        StringRedisTemplate template = RedisTestSupport.stringTemplate(factory);
        template.opsForValue().set("siga:lock:other", "x");
        template.expire("siga:lock:other", Duration.ofMillis(200));
        // Wait long enough for the unrelated expiry to fire (or not).
        Awaitility.await()
                .pollDelay(1, TimeUnit.SECONDS)
                .atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(capturedId.get() == null,
                        "Non-session expirations must not trigger ContainerExpiredEvent"));
    }

    @Test
    void shouldSucceed_WhenNotifyKeyspaceEventsContainsEandA() {
        // RedisSessionExpiryNotifier.assertCoversExpiredEvents accepts the 'A'
        // alias as equivalent to 'x' (per the Redis docs: A is shorthand that includes x). The
        // expression is `value.indexOf('x') >= 0 || value.indexOf('A') >= 0`. Existing fail-fast
        // tests use "" and "AK" (which lacks E), so the A-alias acceptance has no positive test.
        // Pin it: a Redis configured with "EA" must NOT trip the verifier — A covers the expired
        // events the SiGa cleanup pipeline depends on.
        startRedis("EA");
        notifier = new RedisSessionExpiryNotifier(factory, recordingPublisher(new CopyOnWriteArrayList<>()), false);

        assertDoesNotThrow(notifier::subscribe,
                "EA must satisfy the verifier: E (keyevent prefix) + A (alias for all events incl. x)");
    }

    @Test
    void shouldSucceed_WhenNotifyKeyspaceEventsContainsXandEInReverseOrder() {
        // assertCoversExpiredEvents uses indexOf(...) on each character independently — the order
        // of flags in the config string is irrelevant. Operators legitimately set "xE" or "Ex" or
        // "AKE" depending on the parameter group's preferred ordering. Pin the order-agnostic
        // semantic with the inverted form.
        startRedis("xE");
        notifier = new RedisSessionExpiryNotifier(factory, recordingPublisher(new CopyOnWriteArrayList<>()), false);

        assertDoesNotThrow(notifier::subscribe,
                "xE must satisfy the verifier just like Ex — flag order in notify-keyspace-events is irrelevant");
    }

    @Test
    void shouldFailFast_WhenOnlyKeyeventPrefixSet() {
        // assertCoversExpiredEvents requires BOTH `indexOf('E') >= 0` AND `(indexOf('x') >= 0 ||
        // indexOf('A') >= 0)`. The "AK" test covers "missing E"; the empty test covers "missing
        // both". This test pins the "E alone" case (keyevent prefix set but no expired-event
        // flag) — a real-world misconfiguration where an operator enabled keyevent notifications
        // but forgot to include x or A. The error message must mention the flag operators need
        // to fix.
        startRedis("E");
        notifier = new RedisSessionExpiryNotifier(factory, recordingPublisher(new CopyOnWriteArrayList<>()), false);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, notifier::subscribe);
        assertTrue(thrown.getMessage().contains("notify-keyspace-events"),
                "Error must mention the config flag operators need to fix");
        assertTrue(thrown.getMessage().contains("Refusing to start"),
                "Error must make clear this is a fail-fast guard");
    }

    @Test
    void shouldDeliverEventWithEmptySessionId_WhenExpiringKeyIsBareSessionPrefix() {
        // RedisSessionKeys.isSessionKey checks only `key.startsWith(SESSION_PREFIX)` and
        // extractSessionId returns `key.substring(SESSION_PREFIX.length())`. For the malformed
        // key "siga:session:" (the prefix with NO id), isSessionKey returns true and substring
        // returns "" — so RedisSessionExpiryNotifier.handleExpiredKey publishes
        // a ContainerExpiredEvent with sessionId="". This documents the actual boundary behavior;
        // the matching null-id guard in RedisSessionEventListenerTest.shouldNotThrow_When*
        // does NOT cover this empty-string case (it gets past `if (sessionId == null) return;`
        // and runs no-op ZREM calls against members that don't exist — safe but worth
        // pinning so a future refactor that switches to a stricter id pattern surfaces here.
        startRedis("Ex");
        List<Object> events = new CopyOnWriteArrayList<>();
        notifier = new RedisSessionExpiryNotifier(factory, recordingPublisher(events), false);
        notifier.subscribe();

        StringRedisTemplate template = RedisTestSupport.stringTemplate(factory);
        template.opsForValue().set("siga:session:", "x");
        template.expire("siga:session:", Duration.ofMillis(300));

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> events.stream().anyMatch(ContainerExpiredEvent.class::isInstance));

        ContainerExpiredEvent first = (ContainerExpiredEvent) events.stream()
                .filter(ContainerExpiredEvent.class::isInstance)
                .findFirst()
                .orElseThrow();
        assertEquals("", first.sessionId(),
                "Bare 'siga:session:' prefix expiry produces a ContainerExpiredEvent with "
                        + "sessionId=\"\" — pins the actual substring-based extraction behavior");
    }

    private void startRedis(String notifyKeyspaceEventsValue) {
        redis = notifyKeyspaceEventsValue.isEmpty()
                ? new GenericContainer<>(VALKEY_IMAGE).withExposedPorts(REDIS_PORT)
                : new GenericContainer<>(VALKEY_IMAGE)
                .withExposedPorts(REDIS_PORT)
                .withCommand("redis-server", "--notify-keyspace-events", notifyKeyspaceEventsValue);
        redis.start();
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                redis.getHost(), redis.getMappedPort(REDIS_PORT));
        factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
    }

    private static RedisConnectionFactory nonLettuceFactory() {
        return new RedisConnectionFactory() {
            @Override
            public RedisConnection getConnection() {
                return null;
            }

            @Override
            public RedisClusterConnection getClusterConnection() {
                return null;
            }

            @Override
            public RedisSentinelConnection getSentinelConnection() {
                return null;
            }

            @Override
            public boolean getConvertPipelineAndTxResults() {
                return false;
            }

            @Override
            public DataAccessException translateExceptionIfPossible(RuntimeException ex) {
                return null;
            }
        };
    }

    private static ApplicationEventPublisher recordingPublisher(List<Object> recorder) {
        return (ApplicationEventPublisher) recorder::add;
    }
}
