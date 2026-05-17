package ee.openeid.siga.session.redis;

import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.session.SessionStatus;
import ee.openeid.siga.common.session.SignatureSession;
import ee.openeid.siga.session.configuration.SessionStatusReprocessingProperties;
import ee.openeid.siga.session.spi.SessionRemovedEvent;
import ee.openeid.siga.session.spi.SessionUpdatedEvent;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.Toxic;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static ee.openeid.siga.common.model.SigningType.SMART_ID;
import static ee.openeid.siga.common.session.ProcessingStatus.PROCESSING;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that Redis writes stay all-or-nothing when the network connection is cut mid-command.
 *
 * <p>The setup sends each write through Toxiproxy to a real Valkey server. For each scenario,
 * Toxiproxy closes the upstream connection after a configured number of bytes, and a separate
 * direct Redis connection reads the final server state. Each scenario is repeated over a byte
 * range because command sizes vary with serializer, JDK, and Lettuce framing details.
 *
 * <p>Every checked cut point must leave Redis in one of two valid states: the script did not run,
 * or the whole script ran. Any state where only part of the write is visible means the atomicity
 * guarantee is broken.
 */
@Tag("docker")
@Testcontainers
class RedisAtomicityFaultInjectionTest {

    private static final DockerImageName VALKEY_IMAGE = DockerImageName.parse("valkey/valkey:7.2.6-alpine");
    private static final DockerImageName TOXIPROXY_IMAGE = DockerImageName.parse("ghcr.io/shopify/toxiproxy:2.12.0");
    private static final String REDIS_ALIAS = "redis-upstream";
    private static final int REDIS_PORT = 6379;
    private static final int TOXIPROXY_LISTEN_PORT = 8666;

    private static final Network NETWORK = Network.newNetwork();

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(VALKEY_IMAGE)
            .withNetwork(NETWORK)
            .withNetworkAliases(REDIS_ALIAS)
            .withExposedPorts(REDIS_PORT)
            .withCommand("redis-server", "--notify-keyspace-events", "Ex");

    @Container
    private static final ToxiproxyContainer TOXIPROXY = new ToxiproxyContainer(TOXIPROXY_IMAGE)
            .withNetwork(NETWORK);

    private static ToxiproxyClient toxiproxyClient;
    private static Proxy proxy;
    private static LettuceConnectionFactory proxiedFactory;
    private static LettuceConnectionFactory directFactory;
    private static RedisTemplate<String, Object> proxiedTemplate;
    private static RedisTemplate<String, Object> directTemplate;
    private static StringRedisTemplate proxiedStringTemplate;

    @BeforeAll
    static void initClients() throws Exception {
        toxiproxyClient = new ToxiproxyClient(
                TOXIPROXY.getHost(), TOXIPROXY.getControlPort());
        proxy = toxiproxyClient.createProxy(
                "redis", "0.0.0.0:" + TOXIPROXY_LISTEN_PORT, REDIS_ALIAS + ":" + REDIS_PORT);

        // Proxied factory routes through Toxiproxy — this is the connection we fault-inject on.
        RedisStandaloneConfiguration proxiedConfig = new RedisStandaloneConfiguration(
                TOXIPROXY.getHost(), TOXIPROXY.getMappedPort(TOXIPROXY_LISTEN_PORT));
        // Tight client timeouts so a cut connection fails quickly rather than blocking the test.
        // 500ms is enough to let SET round-trip on a healthy connection but short enough that
        // the per-cut-attempt iteration in the search loop stays sub-second.
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(500))
                .shutdownTimeout(Duration.ofMillis(100))
                .build();
        proxiedFactory = new LettuceConnectionFactory(proxiedConfig, clientConfig);
        proxiedFactory.setValidateConnection(false);
        proxiedFactory.afterPropertiesSet();
        proxiedTemplate = RedisTestSupport.sessionTemplate(proxiedFactory);
        proxiedStringTemplate = RedisTestSupport.stringTemplate(proxiedFactory);

        // Direct factory bypasses Toxiproxy — used to inspect server state after the fault so the
        // assertion isn't affected by a still-broken proxy connection.
        RedisStandaloneConfiguration directConfig = new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT));
        directFactory = new LettuceConnectionFactory(directConfig);
        directFactory.afterPropertiesSet();
        directTemplate = RedisTestSupport.sessionTemplate(directFactory);
    }

    @AfterAll
    static void closeClients() {
        if (proxiedFactory != null) proxiedFactory.destroy();
        if (directFactory != null) directFactory.destroy();
    }

    @BeforeEach
    void resetState() throws Exception {
        clearProxyToxics();
        proxy.enable();
        RedisTestSupport.flushAll(directFactory);
    }

    @AfterEach
    void cleanUpAfterFault() throws Exception {
        // After a fault, the proxied factory's pool may hold a half-closed connection. Resetting
        // the proxy state ensures the next test starts with a healthy channel.
        clearProxyToxics();
        proxy.enable();
    }

    // -------------------------------------------------------------------------
    // Verifies that a session value and its TTL are applied together. Spring Data Redis writes the
    // aggregate through a single SET-with-expiry command, so the key is either absent or present
    // with a TTL.
    // -------------------------------------------------------------------------
    @Test
    void shouldKeepUpdateValueAndTtlAtomic_AcrossEveryMidStreamCutPoint() throws Exception {
        RedisSessionStorage storage = new RedisSessionStorage(proxiedTemplate, Duration.ofMinutes(5));
        String sessionId = "v1_svc_fault_orphan";
        String key = RedisSessionKeys.session(sessionId);

        // The SET frame for a tiny HashcodeContainerSession with JDK-serialized empty sidecar maps
        // lands around 600-700 bytes total. Sweep 200-1000 bytes in 20-byte steps to cover both
        // the truncated range and the fully-delivered range; the assertion holds for both — either
        // the command never ran (key absent) or it ran atomically (value + TTL).
        assertNoCutPointProducesOrphanState(
                /* lowBytes */ 200, /* highBytes */ 1_000, /* step */ 20,
                cutBytes -> {
                    try {
                        RedisTestSupport.flushAll(directFactory);
                        clearProxyToxics();
                        proxy.toxics().limitData("hsetExpireCut", ToxicDirection.UPSTREAM, cutBytes);
                        // The write may throw (most cut points) or succeed (cut beyond the EVAL
                        // frame); both are valid, and the invariant is on the post-state.
                        try {
                            storage.update(newSession(sessionId));
                        } catch (RuntimeException ignored) {
                            // Expected when the cut lands inside the EVAL bytes.
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    Boolean hasKey = directTemplate.hasKey(key);
                    Long ttl = directTemplate.getExpire(key, TimeUnit.SECONDS);
                    // The orphan state is (key exists AND ttl == -1). SET-with-expiry atomicity
                    // must rule it out at every cut point in the sweep.
                    return Boolean.TRUE.equals(hasKey) && ttl != null && ttl == -1L;
                });
    }

    // -------------------------------------------------------------------------
    // Verifies that removing one session from both due queues is applied as one Redis operation.
    // The two queue keys share the {reprocess} hashtag, so the EVAL frame can run in one cluster
    // slot and a TCP cut never leaves exactly one queue containing the member.
    // -------------------------------------------------------------------------
    @Test
    void shouldKeepRemoveFromAllQueuesAtomic_AcrossEveryMidStreamCutPoint() throws Exception {
        String sessionId = "v1_svc_fault_remove";
        RedisSessionEventListener listener = new RedisSessionEventListener(
                proxiedStringTemplate, SessionStatusReprocessingProperties.withDefaults());

        // The EVAL frame with two short keys and a short sessionId fits in 100-200 bytes. Sweep
        // 20-250 in 5-byte steps so every cut position around the script frame is exercised.
        assertNoCutPointProducesOrphanState(
                /* lowBytes */ 20, /* highBytes */ 250, /* step */ 5,
                cutBytes -> {
                    try {
                        RedisTestSupport.flushAll(directFactory);
                        directStringOps().add(RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, sessionId, 1L);
                        directStringOps().add(RedisSessionKeys.CERTIFICATE_REPROCESSING_QUEUE, sessionId, 1L);
                        clearProxyToxics();
                        proxy.toxics().limitData("zremPairCut", ToxicDirection.UPSTREAM, cutBytes);

                        try {
                            listener.onSessionRemoved(new SessionRemovedEvent(sessionId));
                        } catch (RuntimeException ignored) {
                            // The EVAL surfaces the truncated-frame error — expected at most cut points.
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    long sigCount = directStringOps().count(
                            RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, 0, Long.MAX_VALUE);
                    long certCount = directStringOps().count(
                            RedisSessionKeys.CERTIFICATE_REPROCESSING_QUEUE, 0, Long.MAX_VALUE);
                    // Partial state: exactly one queue still holds the member. Lua atomicity must
                    // rule it out at every cut point in the sweep — the valid post-states are 0
                    // (script ran completely) or 2 (script never ran).
                    return sigCount + certCount == 1L;
                });
    }

    // -------------------------------------------------------------------------
    // Verifies that recalculating due-queue membership is applied as one Redis operation. The
    // script either adds the due signature member and removes the stale certificate member, or it
    // does neither.
    // -------------------------------------------------------------------------
    @Test
    void shouldKeepOnSessionUpdatedAtomic_AcrossEveryMidStreamCutPoint() throws Exception {
        String sessionId = "v1_svc_fault_update";
        RedisSessionEventListener listener = new RedisSessionEventListener(
                proxiedStringTemplate, SessionStatusReprocessingProperties.withDefaults());

        Session session = HashcodeContainerSession.builder()
                .sessionId(sessionId)
                .clientName("c").serviceName("s").serviceUuid("u")
                .dataFiles(Collections.emptyList())
                .signatures(Collections.emptyList())
                .build();
        Map<String, SignatureSession> signatures = new HashMap<>();
        signatures.put("sig-1", SignatureSession.builder()
                .signingType(SMART_ID)
                .sessionStatus(SessionStatus.builder()
                        .processingStatus(PROCESSING)
                        .processingStatusTimestamp(Instant.now().minus(Duration.ofMinutes(5)))
                        .build())
                .build());
        session.setSignatureSessions(signatures);
        session.setCertificateSessions(new HashMap<>());

        // The disallowed partial state is "signature member added while the stale certificate
        // member survives". Sweep cut points across the script frame to confirm no cut produces
        // that combination.
        assertNoCutPointProducesOrphanState(
                /* lowBytes */ 30, /* highBytes */ 300, /* step */ 5,
                cutBytes -> {
                    try {
                        RedisTestSupport.flushAll(directFactory);
                        // Plant a stale CERTIFICATE-queue member that the upcoming update should
                        // ZREM (the session has no certificate sessions, so the Lua script's
                        // ZREM branch fires on KEYS[2]).
                        directStringOps().add(
                                RedisSessionKeys.CERTIFICATE_REPROCESSING_QUEUE, sessionId, 1L);
                        clearProxyToxics();
                        proxy.toxics().limitData("zaddZremCut", ToxicDirection.UPSTREAM, cutBytes);

                        try {
                            listener.onSessionUpdated(new SessionUpdatedEvent(session));
                        } catch (RuntimeException ignored) {
                            // Truncation surfaces as a runtime error from Lettuce — expected.
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    Double sigScore = directStringOps().score(
                            RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, sessionId);
                    Double certScore = directStringOps().score(
                            RedisSessionKeys.CERTIFICATE_REPROCESSING_QUEUE, sessionId);
                    // Partial state: SIGNATURE ZADD applied AND stale certificate member survived.
                    // Lua atomicity must rule this combination out at every cut point.
                    return sigScore != null && certScore != null;
                });
    }

    /**
     * Walks an upstream byte limit between {@code low} and {@code high} in {@code step} bytes,
     * applying each as a Toxiproxy {@code limitData} toxic and asking the supplied predicate
     * whether the resulting Redis state is the partial-pipeline orphan state. The assertion
     * fails the test on the first cut point where the predicate returns {@code true} — any hit
     * means the Lua atomicity guarantee did not hold for that write.
     *
     * <p>The sweep is robust to JDK-serialiser size variations across Java versions, alphabetical
     * field-ordering changes, and Lettuce framing tweaks because it visits every position in the
     * configured range.
     */
    private static void assertNoCutPointProducesOrphanState(int low, int high, int step,
                                                            java.util.function.IntPredicate orphanState) {
        for (int cutAt = low; cutAt <= high; cutAt += step) {
            assertFalse(orphanState.test(cutAt),
                    "Redis write atomicity must keep the post-state consistent at every cut point, but "
                            + "cutBytes=" + cutAt + " produced the orphan state. Either the "
                            + "command was split across reconnect handling or multiple Redis commands "
                            + "were applied without an atomic boundary.");
        }
        assertTrue(true, "All " + ((high - low) / step + 1) + " cut points kept the post-state consistent.");
    }

    private static void clearProxyToxics() throws Exception {
        for (Toxic toxic : proxy.toxics().getAll()) {
            toxic.remove();
        }
    }

    private static org.springframework.data.redis.core.ZSetOperations<String, String> directStringOps() {
        return RedisTestSupport.stringTemplate(directFactory).opsForZSet();
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
}
