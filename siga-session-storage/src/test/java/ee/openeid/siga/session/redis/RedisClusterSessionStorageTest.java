package ee.openeid.siga.session.redis;

import ee.openeid.siga.common.session.CertificateSession;
import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.common.session.ProcessingStatus;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.session.SessionStatus;
import ee.openeid.siga.common.session.SignatureSession;
import ee.openeid.siga.session.configuration.SessionStatusReprocessingProperties;
import ee.openeid.siga.session.spi.ContainerExpiredEvent;
import ee.openeid.siga.session.spi.SessionUpdatedEvent;
import ee.openeid.siga.session.spi.StatusReprocessingFilter;
import io.lettuce.core.cluster.api.async.AsyncExecutions;
import io.lettuce.core.cluster.event.ClusterTopologyChangedEvent;
import io.lettuce.core.cluster.pubsub.StatefulRedisClusterPubSubConnection;
import io.lettuce.core.cluster.pubsub.api.async.NodeSelectionPubSubAsyncCommands;
import io.lettuce.core.cluster.pubsub.api.async.PubSubAsyncNodeSelection;
import io.lettuce.core.cluster.pubsub.api.async.RedisClusterPubSubAsyncCommands;
import io.lettuce.core.internal.HostAndPort;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.MappingSocketAddressResolver;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.Disposable;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static ee.openeid.siga.common.model.SigningType.SMART_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the Redis Cluster code paths in {@link RedisSessionStorage} and
 * {@link RedisSessionExpiryNotifier}. Spins a 3-master + 3-replica Valkey cluster via
 * Testcontainers and exercises contracts that fail silently in standalone setups: per-master {@code SCAN}
 * accumulation in {@code size()}, single-key value read/write routing across masters, and cluster
 * pub/sub propagation of expiry events.
 */
@Tag("docker")
@Testcontainers
class RedisClusterSessionStorageTest {

    private static final DockerImageName VALKEY_IMAGE = DockerImageName.parse("valkey/valkey:7.2.6-alpine");
    private static final int VALKEY_PORT = 6379;
    private static final int MASTER_NODE_COUNT = 3;
    private static final int CLUSTER_NODE_COUNT = 6;
    private static final Network CLUSTER_NETWORK = Network.newNetwork();

    @Container
    private static final GenericContainer<?> NODE_0 = newValkeyNode(0);
    @Container
    private static final GenericContainer<?> NODE_1 = newValkeyNode(1);
    @Container
    private static final GenericContainer<?> NODE_2 = newValkeyNode(2);
    @Container
    private static final GenericContainer<?> NODE_3 = newValkeyNode(3);
    @Container
    private static final GenericContainer<?> NODE_4 = newValkeyNode(4);
    @Container
    private static final GenericContainer<?> NODE_5 = newValkeyNode(5);

    private static final List<GenericContainer<?>> NODES = List.of(
            NODE_0, NODE_1, NODE_2, NODE_3, NODE_4, NODE_5);
    private static final Map<String, GenericContainer<?>> NODE_BY_ALIAS =
            IntStream.range(0, NODES.size())
                    .boxed()
                    .collect(Collectors.toUnmodifiableMap(
                            RedisClusterSessionStorageTest::nodeAlias, NODES::get));

    private static ClientResources clientResources;
    private static LettuceConnectionFactory factory;
    private static RedisTemplate<String, Object> sessionTemplate;
    private static StringRedisTemplate stringTemplate;

    @BeforeAll
    static void initClients() throws Exception {
        createCluster();

        // The cluster nodes advertise themselves on internal bridge addresses that aren't routable
        // from a Docker Desktop host. Translate every advertised node alias to its exposed
        // Testcontainers host port so Lettuce reaches all masters and replicas.
        clientResources = DefaultClientResources.builder()
                .socketAddressResolver(MappingSocketAddressResolver.create(
                        RedisClusterSessionStorageTest::mapClusterAddress))
                .build();

        RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration(clusterSeedAddresses());
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .clientResources(clientResources)
                .build();
        factory = new LettuceConnectionFactory(clusterConfig, clientConfig);
        factory.afterPropertiesSet();

        sessionTemplate = RedisTestSupport.sessionTemplate(factory);
        stringTemplate = RedisTestSupport.stringTemplate(factory);
    }

    @BeforeEach
    void cleanClusterState() throws Exception {
        // valkey-cli --cluster create assigns the first three nodes as masters; replicas receive
        // FLUSHALL through normal replication.
        for (int i = 0; i < MASTER_NODE_COUNT; i++) {
            var result = NODES.get(i).execInContainer(
                    "valkey-cli", "-p", String.valueOf(VALKEY_PORT), "FLUSHALL");
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("Failed to flush " + nodeAlias(i) + ": "
                        + result.getStdout() + result.getStderr());
            }
        }
    }

    @AfterAll
    static void closeClients() {
        if (factory != null) {
            factory.destroy();
        }
        if (clientResources != null) {
            clientResources.shutdown();
        }
    }

    @Test
    void shouldSucceed_WhenMultiKeyWriteOnCluster() {
        RedisSessionStorage storage = new RedisSessionStorage(sessionTemplate, Duration.ofMinutes(5));
        String sessionId = "v1_svc_cluster_roundtrip";
        Session session = newSession(sessionId);
        Map<String, SignatureSession> signatures = new HashMap<>();
        signatures.put("sig-1", SignatureSession.builder().build());
        Map<String, CertificateSession> certificates = new HashMap<>();
        certificates.put("cert-1", CertificateSession.builder().build());
        session.setSignatureSessions(signatures);
        session.setCertificateSessions(certificates);

        storage.update(session);
        Optional<Session> loaded = storage.get(sessionId);

        assertTrue(loaded.isPresent());
        assertEquals(sessionId, loaded.get().getSessionId());
        assertNotNull(loaded.get().getSignatureSessions());
        assertEquals(1, loaded.get().getSignatureSessions().size());
        assertNotNull(loaded.get().getCertificateSessions());
        assertEquals(1, loaded.get().getCertificateSessions().size());
    }

    @Test
    void shouldSumAcrossMasters_WhenSizeCalledOnCluster() throws Exception {
        RedisSessionStorage storage = new RedisSessionStorage(sessionTemplate, Duration.ofMinutes(5));
        // Hand-picked hashtags whose CRC16 slots fall on different masters with the standard
        // valkey-cli --cluster create slot assignment (master 1: 0-5460, master 2: 5461-10922,
        // master 3: 10923-16383):
        //   {3} -> slot 1584  (master 1)
        //   {1} -> slot 9842  (master 2)
        //   {4} -> slot 14745 (master 3)
        // The countMastersHoldingKeys() assertion in this test verifies that spread holds, so
        // size()'s cross-master aggregation is actually exercised rather than trusted to probability.
        List<String> sessionIds = List.of(
                "v1_svc_cluster_count_{3}",
                "v1_svc_cluster_count_{1}",
                "v1_svc_cluster_count_{4}");
        for (String sessionId : sessionIds) {
            storage.update(newSession(sessionId));
        }

        long mastersWithKeys = countMastersHoldingKeys();
        assertTrue(mastersWithKeys >= 2,
                "Hand-picked hashtags must span >=2 masters for the aggregation check to be meaningful; "
                        + "only " + mastersWithKeys + " master(s) hold keys");
        assertEquals(sessionIds.size(), storage.size());
    }

    private static long countMastersHoldingKeys() throws Exception {
        long mastersWithKeys = 0;
        for (int i = 0; i < MASTER_NODE_COUNT; i++) {
            var result = NODES.get(i).execInContainer(
                    "valkey-cli", "-p", String.valueOf(VALKEY_PORT), "DBSIZE");
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("DBSIZE on " + nodeAlias(i) + " failed: "
                        + result.getStdout() + result.getStderr());
            }
            if (Long.parseLong(result.getStdout().trim()) > 0) {
                mastersWithKeys++;
            }
        }
        return mastersWithKeys;
    }

    private static void setNotifyKeyspaceEventsOnMaster(int index, String value) throws Exception {
        var result = NODES.get(index).execInContainer(
                "valkey-cli", "-p", String.valueOf(VALKEY_PORT),
                "CONFIG", "SET", "notify-keyspace-events", value);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("CONFIG SET notify-keyspace-events on " + nodeAlias(index) + " failed: "
                    + result.getStdout() + result.getStderr());
        }
    }

    @Test
    void shouldDeliverEventToListener_WhenKeyspaceEventFiresOnCluster() throws Exception {
        CountDownLatch eventLatch = new CountDownLatch(1);
        AtomicReference<String> capturedSessionId = new AtomicReference<>();
        ApplicationEventPublisher publisher = event -> {
            if (event instanceof ContainerExpiredEvent expired) {
                capturedSessionId.set(expired.sessionId());
                eventLatch.countDown();
            }
        };
        RedisSessionExpiryNotifier notifier = new RedisSessionExpiryNotifier(factory, publisher, false);
        notifier.subscribe();
        try {
            String sessionId = "v1_svc_cluster_expire";
            // Write the session via the production path, then shorten its TTL so the expiry event
            // fires fast.
            RedisSessionStorage storage = new RedisSessionStorage(sessionTemplate, Duration.ofMinutes(5));
            storage.update(newSession(sessionId));
            sessionTemplate.expire(RedisSessionKeys.session(sessionId), Duration.ofMillis(500));

            assertTrue(eventLatch.await(10, TimeUnit.SECONDS),
                    "ContainerExpiredEvent should reach the listener via cluster pub/sub propagation");
            assertEquals(sessionId, capturedSessionId.get());
        } finally {
            notifier.unsubscribe();
        }
    }

    @Test
    void shouldDeliverEventFromEveryMaster_WhenKeyspaceEventFiresOnCluster() throws Exception {
        // Guards the design decision in RedisSessionExpiryNotifier: Spring Data Redis'
        // RedisMessageListenerContainer / KeyExpirationEventMessageListener subscribe to one
        // (effectively random) cluster node and miss keyevent expiries from every other master
        // (see Spring Data Redis issues #1111 and #1789, and cluster.adoc). The notifier instead
        // uses Lettuce's RedisClusterClient.connectPubSub() + setNodeMessagePropagation(true) so
        // a single psubscribe fans out across every master. If a future refactor "simplifies"
        // back to the Spring listener, at most one of the three keyspace events this test
        // publishes would arrive and this test would fail loudly.
        //
        // Hashtags re-use the {3}/{1}/{4} combination from
        // shouldSumAcrossMasters_WhenSizeCalledOnCluster — already proven to span all three
        // masters under the standard valkey-cli --cluster create slot assignment.
        List<String> sessionIds = List.of(
                "v1_svc_cluster_expire_{3}",
                "v1_svc_cluster_expire_{1}",
                "v1_svc_cluster_expire_{4}");
        Set<String> received = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(sessionIds.size());
        ApplicationEventPublisher publisher = event -> {
            if (event instanceof ContainerExpiredEvent expired && received.add(expired.sessionId())) {
                latch.countDown();
            }
        };
        RedisSessionExpiryNotifier notifier = new RedisSessionExpiryNotifier(factory, publisher, false);
        notifier.subscribe();
        try {
            RedisSessionStorage storage = new RedisSessionStorage(sessionTemplate, Duration.ofMinutes(5));
            for (String sessionId : sessionIds) {
                storage.update(newSession(sessionId));
            }
            // Precondition check before TTLs elapse: if a future Valkey image reshuffles slot
            // ownership and the hashtags collapse onto a single master, this test silently stops
            // protecting against the regression. Fail loudly instead.
            assertEquals(MASTER_NODE_COUNT, countMastersHoldingKeys(),
                    "Hashtags must span every master for the every-master expiry assertion to be meaningful");

            for (String sessionId : sessionIds) {
                sessionTemplate.expire(RedisSessionKeys.session(sessionId), Duration.ofMillis(500));
            }

            assertTrue(latch.await(15, TimeUnit.SECONDS),
                    "Expected ContainerExpiredEvent from every master; received: " + received);
            assertEquals(Set.copyOf(sessionIds), received);
        } finally {
            notifier.unsubscribe();
        }
    }

    @Test
    void shouldFailFast_WhenAnyClusterMasterMissingKeyspaceEventConfig() throws Exception {
        setNotifyKeyspaceEventsOnMaster(1, "K");
        RedisSessionExpiryNotifier notifier = new RedisSessionExpiryNotifier(factory, event -> {
        }, false);
        try {
            IllegalStateException thrown = assertThrows(IllegalStateException.class, notifier::subscribe);
            assertTrue(thrown.getMessage().contains("notify-keyspace-events"));
            assertTrue(thrown.getMessage().contains("cluster master"));
        } finally {
            notifier.unsubscribe();
            setNotifyKeyspaceEventsOnMaster(1, "Ex");
        }
    }

    @Test
    void shouldRetryResubscribe_WhenTopologyChangeResubscribeFails() throws Exception {
        // Regression guard: a transient RedisException from the multi-node PSUBSCRIBE during a
        // real failover must not permanently disable topology recovery. The first synthetic
        // topology event drives a failing re-subscribe; the second event must still reach the
        // notifier and schedule another PSUBSCRIBE attempt.
        CountDownLatch firstAttempt = new CountDownLatch(1);
        CountDownLatch secondAttempt = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        StatefulRedisClusterPubSubConnection<String, String> failingConnection =
                failingOnceClusterConnection(firstAttempt, secondAttempt, attempts);

        RedisSessionExpiryNotifier notifier = new RedisSessionExpiryNotifier(factory, event -> {
        }, false);
        notifier.subscribe();
        try {
            Field connField = RedisSessionExpiryNotifier.class.getDeclaredField("clusterConnection");
            connField.setAccessible(true);

            StatefulRedisClusterPubSubConnection<?, ?> realConnection =
                    (StatefulRedisClusterPubSubConnection<?, ?>) connField.get(notifier);
            realConnection.close();
            connField.set(notifier, failingConnection);

            publishTopologyChangedEventAndWaitForDispatch();
            assertTrue(firstAttempt.await(5, TimeUnit.SECONDS),
                    "First topology event must trigger the failing re-PSUBSCRIBE attempt");

            publishTopologyChangedEventAndWaitForDispatch();
            assertTrue(secondAttempt.await(5, TimeUnit.SECONDS),
                    "Second topology event must still trigger re-PSUBSCRIBE after the first "
                            + "attempt failed; otherwise the topology listener was cancelled");
            assertTrue(attempts.get() >= 2,
                    "Expected at least two re-PSUBSCRIBE attempts, got " + attempts.get());
        } finally {
            notifier.unsubscribe();
        }
    }

    @SuppressWarnings("unchecked")
    private static StatefulRedisClusterPubSubConnection<String, String> failingOnceClusterConnection(
            CountDownLatch firstAttempt, CountDownLatch secondAttempt, AtomicInteger attempts) {
        StatefulRedisClusterPubSubConnection<String, String> connection =
                Mockito.mock(StatefulRedisClusterPubSubConnection.class);
        RedisClusterPubSubAsyncCommands<String, String> async =
                Mockito.mock(RedisClusterPubSubAsyncCommands.class);
        PubSubAsyncNodeSelection<String, String> upstream =
                Mockito.mock(PubSubAsyncNodeSelection.class);
        NodeSelectionPubSubAsyncCommands<String, String> commands =
                Mockito.mock(NodeSelectionPubSubAsyncCommands.class);
        AsyncExecutions<Void> emptyExecutions = Mockito.mock(AsyncExecutions.class);

        Mockito.when(connection.async()).thenReturn(async);
        Mockito.when(async.upstream()).thenReturn(upstream);
        Mockito.when(upstream.commands()).thenReturn(commands);
        Mockito.when(emptyExecutions.asMap()).thenReturn(Map.of());
        Mockito.when(commands.psubscribe(Mockito.<String>any())).thenAnswer(invocation -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
                firstAttempt.countDown();
                throw new IllegalStateException("Simulated transient PSUBSCRIBE failure");
            }
            secondAttempt.countDown();
            return emptyExecutions;
        });

        return connection;
    }

    private static void publishTopologyChangedEventAndWaitForDispatch() throws InterruptedException {
        CountDownLatch sentinelReceived = new CountDownLatch(1);
        Disposable sentinel = clientResources.eventBus().get()
                .filter(ClusterTopologyChangedEvent.class::isInstance)
                .subscribe(e -> sentinelReceived.countDown());
        try {
            clientResources.eventBus().publish(new ClusterTopologyChangedEvent(List.of(), List.of()));
            assertTrue(sentinelReceived.await(5, TimeUnit.SECONDS),
                    "Synthetic ClusterTopologyChangedEvent never reached an EventBus subscriber");
        } finally {
            sentinel.dispose();
        }
    }

    @Test
    void shouldSucceed_WhenLockOperationsOnCluster() {
        // Mirror RedisSessionConfiguration's production wiring: same prefix, same lock type.
        // Without the {lock} hashtag in LOCK_REGISTRY_KEY, the PUB_SUB_LOCK unlock script CROSSSLOTs
        // on Valkey 7.2+ cluster because the script touches both the lock key and a derived pub/sub
        // channel. Exercising obtain + tryLock + unlock across several distinct paths is what
        // forces both Lua scripts (acquire and unlock-notify) to run end-to-end.
        RedisLockRegistry registry = new RedisLockRegistry(factory,
                RedisSessionConfiguration.LOCK_REGISTRY_KEY, 5_000L);
        registry.setRedisLockType(RedisLockRegistry.RedisLockType.PUB_SUB_LOCK);
        try {
            for (String sessionId : List.of("v1_svc_lock_A", "v1_svc_lock_B", "v1_svc_lock_C")) {
                Lock lock = registry.obtain(sessionId);
                assertTrue(lock.tryLock(), "tryLock failed for " + sessionId);
                try {
                    /* no-op */
                } finally {
                    lock.unlock();
                }
            }
        } finally {
            registry.destroy();
        }
    }

    @Test
    void shouldUpdateReprocessingQueues_WhenLuaRunsOnCluster() {
        // The two siga:{reprocess}:* ZSETs share a hashtag, so the cross-key Lua scripts in
        // RedisSessionEventListener run in a single Redis Cluster slot without CROSSSLOT.
        RedisSessionStorage storage = new RedisSessionStorage(sessionTemplate, Duration.ofMinutes(5));
        RedisSessionEventListener listener = new RedisSessionEventListener(
                stringTemplate, SessionStatusReprocessingProperties.withDefaults());
        String sessionId = "v1_svc_reprocess_queue_cluster";
        Session session = newSessionPopulatingAllDueQueues(sessionId);

        storage.update(session);
        listener.onSessionUpdated(new SessionUpdatedEvent(session));

        assertNotNull(stringTemplate.opsForZSet().score(
                RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, sessionId));
        assertNotNull(stringTemplate.opsForZSet().score(
                RedisSessionKeys.CERTIFICATE_REPROCESSING_QUEUE, sessionId));

        listener.onContainerExpired(new ContainerExpiredEvent(sessionId));

        assertNull(stringTemplate.opsForZSet().score(
                RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, sessionId));
        assertNull(stringTemplate.opsForZSet().score(
                RedisSessionKeys.CERTIFICATE_REPROCESSING_QUEUE, sessionId));
    }

    @Test
    void shouldCleanOrphanAcrossClusterSlots_WhenScanRunsAfterDroppedExpiryEvent() throws Exception {
        // Simulates a dropped Redis keyspace expiry event leaving stale ZSET members behind on
        // cluster: the canonical session value is gone but the siga:{reprocess}:signature member
        // remains. verifyCandidate's per-member peek (RedisSessionStatusScanner#verifyCandidate)
        // must route to each orphan's session-value slot independently — the ZSET key lives in
        // the {reprocess} slot, while each orphan value lives on whatever master its
        // hashtag's slot owns. A future refactor that batched verifyCandidate into a Lua script
        // or MULTI/EXEC would CROSSSLOT on Valkey 7.2+ and silently leak orphans.
        //
        // The {3}/{1}/{4} hashtag spread is proven by shouldSumAcrossMasters_WhenSizeCalledOnCluster
        // and shouldDeliverEventFromEveryMaster_WhenKeyspaceEventFiresOnCluster — all three
        // session values land on three different masters, so the orphan-cleanup peek truly
        // traverses every cluster slot.
        RedisSessionStorage storage = new RedisSessionStorage(sessionTemplate, Duration.ofMinutes(5));
        RedisSessionStatusScanner scanner = new RedisSessionStatusScanner(
                stringTemplate, storage, SessionStatusReprocessingProperties.withDefaults(), 10);

        List<String> orphanIds = List.of(
                "v1_svc_reprocess_orphan_{3}",
                "v1_svc_reprocess_orphan_{1}",
                "v1_svc_reprocess_orphan_{4}");

        // Seed real session values first so countMastersHoldingKeys can validate the hashtag
        // spread, then delete each value to simulate the dropped-expiry-event orphan condition.
        for (String orphanId : orphanIds) {
            storage.update(newSession(orphanId));
        }
        assertEquals(MASTER_NODE_COUNT, countMastersHoldingKeys(),
                "Orphan sessionIds must span every master so verifyCandidate's per-member peek "
                        + "routes across slots — otherwise this test silently degrades into a "
                        + "standalone-equivalent check");
        for (String orphanId : orphanIds) {
            sessionTemplate.delete(RedisSessionKeys.session(orphanId));
        }

        // Stale ZSET entries pointing at the now-deleted values — score 1 is well in the past so
        // ZRANGEBYSCORE picks them up on the very first scan.
        for (String orphanId : orphanIds) {
            stringTemplate.opsForZSet().add(
                    RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, orphanId, 1L);
        }

        List<String> emitted = new ArrayList<>();
        Instant now = Instant.now();
        scanner.scanSignatureSessions(
                new StatusReprocessingFilter(Long.MAX_VALUE, now, now), emitted::add);

        assertTrue(emitted.isEmpty(),
                "Orphan sessions whose value is gone must not be emitted to the consumer");
        for (String orphanId : orphanIds) {
            assertNull(stringTemplate.opsForZSet().score(
                            RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, orphanId),
                    "Orphan " + orphanId + " must be removed from the signature due-index — "
                            + "verifyCandidate's per-member peek must route to its slot independently");
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

    private static Session newSessionPopulatingAllDueQueues(String sessionId) {
        Session session = newSession(sessionId);
        Map<String, SignatureSession> signatures = new HashMap<>();
        signatures.put("sig-processing", SignatureSession.builder()
                .signingType(SMART_ID)
                .sessionStatus(SessionStatus.builder()
                        .processingStatus(ProcessingStatus.PROCESSING)
                        .processingStatusTimestamp(Instant.now().minus(Duration.ofMinutes(10)))
                        .build())
                .build());
        signatures.put("sig-exception", SignatureSession.builder()
                .signingType(SMART_ID)
                .sessionStatus(SessionStatus.builder()
                        .processingStatus(ProcessingStatus.EXCEPTION)
                        .processingStatusTimestamp(Instant.now().minus(Duration.ofMinutes(8)))
                        .build())
                .build());
        Map<String, CertificateSession> certificates = new HashMap<>();
        certificates.put("cert-processing", CertificateSession.builder()
                .sessionStatus(SessionStatus.builder()
                        .processingStatus(ProcessingStatus.PROCESSING)
                        .processingStatusTimestamp(Instant.now().minus(Duration.ofMinutes(6)))
                        .build())
                .build());
        certificates.put("cert-exception", CertificateSession.builder()
                .sessionStatus(SessionStatus.builder()
                        .processingStatus(ProcessingStatus.EXCEPTION)
                        .processingStatusTimestamp(Instant.now().minus(Duration.ofMinutes(4)))
                        .build())
                .build());
        session.setSignatureSessions(signatures);
        session.setCertificateSessions(certificates);
        return session;
    }

    private static GenericContainer<?> newValkeyNode(int index) {
        return new GenericContainer<>(VALKEY_IMAGE)
                .withNetwork(CLUSTER_NETWORK)
                .withNetworkAliases(nodeAlias(index))
                .withExposedPorts(VALKEY_PORT)
                .withCommand(
                        "valkey-server",
                        "--bind", "0.0.0.0",
                        "--protected-mode", "no",
                        "--cluster-enabled", "yes",
                        "--cluster-config-file", "nodes.conf",
                        "--cluster-node-timeout", "5000",
                        "--cluster-announce-hostname", nodeAlias(index),
                        "--cluster-announce-port", String.valueOf(VALKEY_PORT),
                        "--cluster-preferred-endpoint-type", "hostname",
                        "--notify-keyspace-events", "Ex",
                        "--appendonly", "no")
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofMinutes(2));
    }

    private static void createCluster() throws Exception {
        List<String> command = new ArrayList<>();
        command.add("valkey-cli");
        command.add("--cluster");
        command.add("create");
        for (int i = 0; i < CLUSTER_NODE_COUNT; i++) {
            command.add(nodeAlias(i) + ":" + VALKEY_PORT);
        }
        command.add("--cluster-replicas");
        command.add("1");
        command.add("--cluster-yes");

        var result = NODE_0.execInContainer(command.toArray(String[]::new));
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("Failed to create Valkey cluster: "
                    + result.getStdout() + result.getStderr());
        }
        waitUntilClusterReady();
    }

    private static void waitUntilClusterReady() {
        // Wait for full convergence: not just cluster_state:ok (which flips as soon as the local
        // node believes it's operational), but all 16384 slots assigned and healthy, every node
        // gossiped, and the expected master count formed by --cluster-replicas 1. This avoids
        // intermittent MOVED-to-unknown-node failures on slow CI boxes.
        AtomicReference<String> lastOutput = new AtomicReference<>("");
        try {
            Awaitility.await()
                    .atMost(30, TimeUnit.SECONDS)
                    .pollInterval(500, TimeUnit.MILLISECONDS)
                    .until(() -> {
                        var result = NODE_0.execInContainer(
                                "valkey-cli", "-p", String.valueOf(VALKEY_PORT), "cluster", "info");
                        String output = result.getStdout() + result.getStderr();
                        lastOutput.set(output);
                        return result.getExitCode() == 0
                                && output.contains("cluster_state:ok")
                                && output.contains("cluster_slots_assigned:16384")
                                && output.contains("cluster_slots_ok:16384")
                                && output.contains("cluster_known_nodes:" + CLUSTER_NODE_COUNT)
                                && output.contains("cluster_size:" + MASTER_NODE_COUNT);
                    });
        } catch (ConditionTimeoutException e) {
            throw new IllegalStateException("Valkey cluster did not become ready: " + lastOutput.get(), e);
        }
    }

    private static HostAndPort mapClusterAddress(HostAndPort hostAndPort) {
        GenericContainer<?> node = NODE_BY_ALIAS.get(hostAndPort.getHostText());
        if (node == null || hostAndPort.getPort() != VALKEY_PORT) {
            return hostAndPort;
        }
        return HostAndPort.of(node.getHost(), node.getMappedPort(VALKEY_PORT));
    }

    private static List<String> clusterSeedAddresses() {
        return NODES.subList(0, MASTER_NODE_COUNT).stream()
                .map(node -> node.getHost() + ":" + node.getMappedPort(VALKEY_PORT))
                .toList();
    }

    private static String nodeAlias(int index) {
        return "valkey-node-" + index;
    }
}
