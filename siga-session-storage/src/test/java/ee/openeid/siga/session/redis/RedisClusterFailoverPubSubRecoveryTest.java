package ee.openeid.siga.session.redis;

import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.session.spi.ContainerExpiredEvent;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.internal.HostAndPort;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.MappingSocketAddressResolver;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the expiry-event subscription follows Redis Cluster failover.
 *
 * <p>The test starts a cluster, subscribes to key expiry events, and confirms an event arrives
 * from the original master. It then stops that master, waits for a replica to be promoted, forces
 * Lettuce to refresh its topology, and confirms an expiry event from the promoted master is
 * delivered too.
 *
 * <p>The expected behavior is that each topology-change event repeats {@code PSUBSCRIBE} against
 * the current upstream nodes, so newly-promoted masters receive the pattern subscription.
 *
 * <p>This file is intentionally a single test method: it kills a master container mid-flight,
 * which leaves the shared Testcontainers cluster in a degraded state. Splitting into multiple
 * methods would require either a much slower per-test cluster or a non-obvious node-restart
 * dance.
 */
@Tag("docker")
@Testcontainers
class RedisClusterFailoverPubSubRecoveryTest {

    private static final DockerImageName VALKEY_IMAGE = DockerImageName.parse("valkey/valkey:7.2.6-alpine");
    private static final int VALKEY_PORT = 6379;
    private static final int MASTER_NODE_COUNT = 3;
    private static final int CLUSTER_NODE_COUNT = 6;
    // Hand-picked hashtag landing in master 2's slot range (5461-10922) per the standard
    // valkey-cli --cluster create slot assignment. {1} → CRC16 slot 9842.
    private static final String MASTER_2_HASHTAG = "{1}";
    private static final Network CLUSTER_NETWORK = Network.newNetwork();

    // The first three seed nodes become masters; the remaining three become replicas. valkey-cli
    // assigns each replica to a master in some order — the specific master→replica pairing is NOT
    // controllable from the outside, so we do not assume NODE_4 (or any specific node) is the
    // replica of NODE_1. waitUntilReplicaPromoted() instead waits for any healthy master to hold
    // the 5461-10922 slot range.
    @Container
    private static final GenericContainer<?> NODE_0 = newValkeyNode(0); // master 1 (0-5460)
    @Container
    private static final GenericContainer<?> NODE_1 = newValkeyNode(1); // master 2 (5461-10922) — the one we kill
    @Container
    private static final GenericContainer<?> NODE_2 = newValkeyNode(2); // master 3 (10923-16383)
    @Container
    private static final GenericContainer<?> NODE_3 = newValkeyNode(3); // replica, assignment non-deterministic
    @Container
    private static final GenericContainer<?> NODE_4 = newValkeyNode(4); // replica, assignment non-deterministic
    @Container
    private static final GenericContainer<?> NODE_5 = newValkeyNode(5); // replica, assignment non-deterministic

    private static final List<GenericContainer<?>> NODES = List.of(
            NODE_0, NODE_1, NODE_2, NODE_3, NODE_4, NODE_5);
    private static final Map<String, GenericContainer<?>> NODE_BY_ALIAS =
            IntStream.range(0, NODES.size())
                    .boxed()
                    .collect(Collectors.toUnmodifiableMap(
                            RedisClusterFailoverPubSubRecoveryTest::nodeAlias, NODES::get));

    private static ClientResources clientResources;
    private static LettuceConnectionFactory factory;
    private static RedisTemplate<String, Object> sessionTemplate;

    @BeforeAll
    static void initClients() throws Exception {
        createCluster();

        clientResources = DefaultClientResources.builder()
                .socketAddressResolver(MappingSocketAddressResolver.create(
                        RedisClusterFailoverPubSubRecoveryTest::mapClusterAddress))
                .build();

        RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration(clusterSeedAddresses());
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .clientResources(clientResources)
                .build();
        factory = new LettuceConnectionFactory(clusterConfig, clientConfig);
        factory.afterPropertiesSet();
        sessionTemplate = RedisTestSupport.sessionTemplate(factory);
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
    void shouldRedeliverPubSubMessages_FromPromotedReplica_AfterMasterFailover() throws Exception {
        // -----------------------------------------------------------------
        // Step 1: Subscribe to expiry notifications. The initial upstream selection covers
        // the current masters; a topology-change listener later repeats the subscription so
        // newly-promoted masters are covered too.
        // -----------------------------------------------------------------
        Set<String> receivedSessionIds = ConcurrentHashMap.newKeySet();
        List<CountDownLatch> latches = new ArrayList<>();
        ApplicationEventPublisher publisher = event -> {
            if (event instanceof ContainerExpiredEvent expired) {
                receivedSessionIds.add(expired.sessionId());
                synchronized (latches) {
                    latches.forEach(CountDownLatch::countDown);
                }
            }
        };
        RedisSessionExpiryNotifier notifier = new RedisSessionExpiryNotifier(factory, publisher, false);
        notifier.subscribe();

        try {
            RedisSessionStorage storage = new RedisSessionStorage(sessionTemplate, Duration.ofMinutes(5));

            // -----------------------------------------------------------------
            // Step 2: Baseline — confirm the notifier IS delivering events for keys on master 2
            // BEFORE the failover. This confirms the initial subscription is healthy and the
            // cluster converged with NODE_1 holding the {1} slot.
            // -----------------------------------------------------------------
            String preFailoverSessionId = "v1_svc_pre_failover_" + MASTER_2_HASHTAG;
            CountDownLatch preLatch = registerLatch(latches);
            storage.update(newSession(preFailoverSessionId));
            sessionTemplate.expire(RedisSessionKeys.session(preFailoverSessionId), Duration.ofMillis(500));

            assertTrue(preLatch.await(15, TimeUnit.SECONDS),
                    "Baseline: notifier must receive pre-failover expiry event on master 2; otherwise "
                            + "the test setup is broken before the cluster-failover scenario begins. "
                            + "Received so far: " + receivedSessionIds);
            assertTrue(receivedSessionIds.contains(preFailoverSessionId));

            // -----------------------------------------------------------------
            // Step 3: Force a failover by shutting down master 2 (NODE_1). Cluster-node-timeout=5s
            // means the remaining masters mark NODE_1 as failing within about 5s, and one of its
            // replicas is promoted shortly after via the cluster failover handshake.
            // -----------------------------------------------------------------
            var shutdown = NODE_1.execInContainer(
                    "valkey-cli", "-p", String.valueOf(VALKEY_PORT), "SHUTDOWN", "NOSAVE");
            assertTrue(shutdown.getExitCode() == 0 || shutdown.getStderr().contains("connection lost"),
                    "Failed to shut down NODE_1 (master 2): "
                            + shutdown.getStdout() + shutdown.getStderr());

            waitUntilReplicaPromoted();

            // The local connection factory is built without periodic cluster refresh so this
            // scenario is not tied to a background timer. Forcing the refresh triggers the same
            // ClusterTopologyChangedEvent the periodic / adaptive refresh publishes at runtime
            // (Spring Boot's auto-config wires those in production via
            // spring.data.redis.lettuce.cluster.refresh.{period,adaptive}); both code paths fan
            // out through the same EventBus the notifier listens on.
            ((RedisClusterClient) factory.getNativeClient()).refreshPartitions();

            // Lettuce's EventBus dispatches ClusterTopologyChangedEvent asynchronously
            // (DefaultEventBus#get() composes `.publishOn(scheduler)`), so refreshPartitions()
            // can return before the notifier's re-PSUBSCRIBE lambda actually runs. Probe the
            // promoted master directly for its server-side pattern-subscription count so the
            // test doesn't race the scheduler — under full-suite JVM contention the immediate
            // re-subscribe can fire well after a 500ms-TTL key has already expired, in which
            // case Redis never redelivers the keyevent and the post-failover latch never fires.
            GenericContainer<?> promotedMaster = promotedMasterForSlotRange();
            awaitReSubscribeOnPromotedMaster(promotedMaster);

            // -----------------------------------------------------------------
            // Step 4: Post-failover write. After the topology refresh, Lettuce routes the
            // SET-with-expiry to the promoted replica (the new master for the {1} slot), and the
            // topology-change handler has re-PSUBSCRIBE'd the new master so its expiry event is
            // delivered.
            // -----------------------------------------------------------------
            String postFailoverSessionId = "v1_svc_post_failover_" + MASTER_2_HASHTAG;
            CountDownLatch postLatch = registerLatch(latches);
            storage.update(newSession(postFailoverSessionId));
            sessionTemplate.expire(RedisSessionKeys.session(postFailoverSessionId), Duration.ofMillis(500));

            // -----------------------------------------------------------------
            // Step 5: The post-failover expiry event must arrive within a reasonable window. The
            // 10s window is generous enough to absorb cluster-side bookkeeping after the failover
            // (gossip, slot reassignment, the replica accepting writes) without slowing the test
            // when the re-subscription is working.
            // -----------------------------------------------------------------
            assertTrue(postLatch.await(10, TimeUnit.SECONDS),
                    "Notifier must redeliver the post-failover expiry event for "
                            + postFailoverSessionId + " within 10s. If the latch never fires, the "
                            + "topology-change listener is either not registered or its "
                            + "re-PSUBSCRIBE call did not reach the promoted master.");
            assertTrue(receivedSessionIds.contains(postFailoverSessionId),
                    "Received sessionIds must include the post-failover one. Actual: "
                            + receivedSessionIds);
        } finally {
            notifier.unsubscribe();
        }
    }

    private static CountDownLatch registerLatch(List<CountDownLatch> latches) {
        CountDownLatch latch = new CountDownLatch(1);
        synchronized (latches) {
            latches.add(latch);
        }
        return latch;
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

    /**
     * After NODE_1 (master 2) is shut down, one of its replicas — whichever node valkey-cli
     * assigned during {@code --cluster-replicas 1} (not deterministic from outside) — is
     * promoted. Wait until the cluster reports a healthy master holding the 5461-10922 slot
     * range and the failed node is acknowledged as such.
     */
    private static void waitUntilReplicaPromoted() {
        AtomicReference<String> lastOutput = new AtomicReference<>("");
        try {
            Awaitility.await()
                    .atMost(45, TimeUnit.SECONDS)
                    .pollInterval(500, TimeUnit.MILLISECONDS)
                    .until(() -> {
                        var result = NODE_0.execInContainer(
                                "valkey-cli", "-p", String.valueOf(VALKEY_PORT), "cluster", "nodes");
                        String output = result.getStdout();
                        lastOutput.set(output);
                        if (result.getExitCode() != 0) {
                            return false;
                        }
                        boolean nodeOneMarkedFail = false;
                        boolean slotsReassigned = false;
                        for (String line : output.split("\n")) {
                            if (line.contains(nodeAlias(1)) && line.contains("fail")) {
                                nodeOneMarkedFail = true;
                            }
                            // A healthy promoted master holds the slot range; its line ends with
                            // "connected 5461-10922" and the role token is "master" or
                            // "myself,master" — but never "master,fail".
                            if (line.contains("5461-10922") && !line.contains("fail")
                                    && (line.contains(" master ") || line.contains(",master "))) {
                                slotsReassigned = true;
                            }
                        }
                        return nodeOneMarkedFail && slotsReassigned;
                    });
        } catch (ConditionTimeoutException e) {
            throw new IllegalStateException(
                    "No replica got promoted to master for slots 5461-10922 within 45s. "
                            + "Cluster topology:\n" + lastOutput.get(), e);
        }
    }

    /**
     * Locate the container that currently holds the 5461-10922 slot range as master. After
     * NODE_1's shutdown, that is one of NODE_3..NODE_5 — which one is decided by valkey-cli's
     * non-deterministic replica-to-master pairing, so the test must read it back from cluster
     * state rather than assume.
     */
    private static GenericContainer<?> promotedMasterForSlotRange() throws Exception {
        var result = NODE_0.execInContainer(
                "valkey-cli", "-p", String.valueOf(VALKEY_PORT), "cluster", "nodes");
        for (String line : result.getStdout().split("\n")) {
            if (!line.contains("5461-10922") || line.contains("fail")) {
                continue;
            }
            if (!line.contains(" master ") && !line.contains(",master ")) {
                continue;
            }
            // CLUSTER NODES line format: "<id> <ip:port@cport,hostname> <flags> ..."
            // The announce-hostname is appended after a comma to the endpoint token.
            String[] parts = line.split("\\s+");
            if (parts.length < 2) {
                continue;
            }
            String endpoint = parts[1];
            int commaIdx = endpoint.lastIndexOf(',');
            if (commaIdx < 0) {
                continue;
            }
            String hostname = endpoint.substring(commaIdx + 1);
            GenericContainer<?> node = NODE_BY_ALIAS.get(hostname);
            if (node != null) {
                return node;
            }
        }
        throw new IllegalStateException(
                "No container found holding slots 5461-10922 as master.\n" + result.getStdout());
    }

    /**
     * Block until the promoted master reports at least one pattern subscription via {@code
     * PUBSUB NUMPAT}. The notifier's topology-change handler and periodic reconciliation run
     * asynchronously, so the only reliable way to know re-PSUBSCRIBE has reached the promoted
     * master is to ask the server itself. NUMPAT is per-node — the promoted master had zero
     * patterns as a replica, so any value &gt;= 1 proves the re-subscribe landed.
     *
     * <p>The 25s budget is generous enough to cover the first topology-change handler attempt
     * (which can take a few seconds under full-suite JVM contention to fire on the EventBus
     * scheduler) plus several periodic reconciliation ticks (5s interval — see
     * {@code RedisSessionExpiryNotifier.RECONCILE_INTERVAL}) in case the inline re-PSUBSCRIBE
     * lost a race with the cluster's pub/sub-connection setup to the freshly-promoted master.
     */
    private static void awaitReSubscribeOnPromotedMaster(GenericContainer<?> promoted) {
        AtomicReference<String> lastOutput = new AtomicReference<>("");
        try {
            Awaitility.await()
                    .atMost(25, TimeUnit.SECONDS)
                    .pollInterval(200, TimeUnit.MILLISECONDS)
                    .until(() -> {
                        var result = promoted.execInContainer(
                                "valkey-cli", "-p", String.valueOf(VALKEY_PORT),
                                "PUBSUB", "NUMPAT");
                        String stdout = result.getStdout().trim();
                        lastOutput.set(stdout);
                        if (result.getExitCode() != 0) {
                            return false;
                        }
                        try {
                            return Integer.parseInt(stdout) >= 1;
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    });
        } catch (ConditionTimeoutException e) {
            throw new IllegalStateException(
                    "Promoted master never reported a pattern subscription via PUBSUB NUMPAT "
                            + "within 25s. The notifier did not re-PSUBSCRIBE on the promoted "
                            + "master via topology-change handling or periodic reconciliation. "
                            + "Last NUMPAT output: "
                            + lastOutput.get(), e);
        }
    }

    private static HostAndPort mapClusterAddress(HostAndPort hostAndPort) {
        GenericContainer<?> node = NODE_BY_ALIAS.get(hostAndPort.getHostText());
        if (node == null || hostAndPort.getPort() != VALKEY_PORT) {
            return hostAndPort;
        }
        if (!node.isRunning()) {
            // After NODE_1 is shut down, return its last-known mapping unchanged so Lettuce's
            // connection attempts fail fast rather than block on DNS — the test relies on
            // Lettuce's MOVED-redirect topology refresh, not on pre-failover routing.
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
