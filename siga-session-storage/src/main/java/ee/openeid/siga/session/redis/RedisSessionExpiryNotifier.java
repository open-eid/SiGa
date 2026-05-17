package ee.openeid.siga.session.redis;

import ee.openeid.siga.session.spi.ContainerExpiredEvent;
import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.async.AsyncExecutions;
import io.lettuce.core.cluster.event.ClusterTopologyChangedEvent;
import io.lettuce.core.cluster.models.partitions.RedisClusterNode;
import io.lettuce.core.cluster.pubsub.RedisClusterPubSubAdapter;
import io.lettuce.core.cluster.pubsub.StatefulRedisClusterPubSubConnection;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import reactor.core.Disposable;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Bridges Redis keyspace expiry notifications into Spring {@link ContainerExpiredEvent}s,
 * mirroring {@code IgniteSessionExpiryNotifier} on the Redis side.
 *
 * <p>Redis must be started with {@code notify-keyspace-events} including {@code E} (keyevent
 * prefix) and {@code x} (or the {@code A} alias). Startup verifies this via {@code CONFIG GET}
 * — every master on Cluster — and fails fast otherwise; without the flags expired session keys
 * vanish without publishing the cleanup event consumed by auth connection cleanup and Redis
 * reprocessing queue cleanup. Set
 * {@code siga.session-storage.redis.skip-keyspace-events-verification=true} on managed services
 * that block {@code CONFIG GET}; the operator then owns the parameter group.
 *
 * <p>The pub/sub subscription path is Lettuce-only because Spring Data Redis does not expose a
 * backend-agnostic cluster subscription that covers every master node. Unsupported connection
 * factories or native clients abort startup instead of silently disabling expiry cleanup.
 */
@Slf4j
@RequiredArgsConstructor
class RedisSessionExpiryNotifier {

    private static final String EXPIRED_PATTERN = "__keyevent@*__:expired";
    private static final String NOTIFY_KEYSPACE_EVENTS = "notify-keyspace-events";
    private static final Duration CLUSTER_RESUBSCRIBE_INTERVAL = Duration.ofSeconds(5);

    @NonNull
    private final RedisConnectionFactory connectionFactory;
    @NonNull
    private final ApplicationEventPublisher eventPublisher;
    private final boolean skipKeyspaceEventsVerification;

    // Written by the Spring lifecycle threads (@PostConstruct and @PreDestroy are not guaranteed
    // to run on the same thread) and read by Lettuce event-bus threads and the resubscribe
    // executor. Volatile so every reader sees either null or the fully constructed value without
    // relying on incidental happens-before edges from executor submission, Reactor subscription
    // internals or Spring's context startup/shutdown lock.
    private volatile @Nullable StatefulRedisPubSubConnection<String, String> standaloneConnection;
    private volatile @Nullable StatefulRedisClusterPubSubConnection<String, String> clusterConnection;
    private volatile @Nullable Disposable topologyChangeSubscription;
    private volatile @Nullable ScheduledExecutorService clusterResubscribeExecutor;
    private volatile boolean clusterSubscribed;

    @PostConstruct
    void subscribe() {
        if (!(connectionFactory instanceof LettuceConnectionFactory lettuce)) {
            throw unsupportedFactory(connectionFactory);
        }
        AbstractRedisClient client = lettuce.getNativeClient();
        if (skipKeyspaceEventsVerification) {
            log.warn("""
                    Skipping notify-keyspace-events verification per \
                    siga.session-storage.redis.skip-keyspace-events-verification=true. \
                    Operator is responsible for ensuring the server has notify-keyspace-events \
                    configured to include 'E' and 'x'/'A' (typically via a managed-service \
                    parameter group); without it container expiry cleanup silently breaks.""");
        } else {
            verifyKeyspaceEvents();
        }
        if (client instanceof RedisClusterClient clusterClient) {
            subscribeCluster(clusterClient);
        } else if (client instanceof RedisClient redisClient) {
            subscribeStandalone(redisClient);
        } else {
            throw new IllegalStateException("Unsupported Lettuce native client type");
        }
    }

    @PreDestroy
    void unsubscribe() {
        clusterSubscribed = false;
        Disposable topologySubscription = topologyChangeSubscription;
        if (topologySubscription != null) {
            topologySubscription.dispose();
            topologyChangeSubscription = null;
        }
        ScheduledExecutorService resubscribeExecutor = clusterResubscribeExecutor;
        if (resubscribeExecutor != null) {
            resubscribeExecutor.shutdownNow();
            clusterResubscribeExecutor = null;
        }
        StatefulRedisPubSubConnection<String, String> standalone = standaloneConnection;
        if (standalone != null) {
            standalone.close();
            standaloneConnection = null;
        }
        StatefulRedisClusterPubSubConnection<String, String> cluster = clusterConnection;
        if (cluster != null) {
            cluster.close();
            clusterConnection = null;
        }
    }

    private void subscribeCluster(RedisClusterClient client) {
        StatefulRedisClusterPubSubConnection<String, String> connection = client.connectPubSub();
        connection.setNodeMessagePropagation(true);
        connection.addListener(new RedisClusterPubSubAdapter<>() {
            @Override
            public void message(RedisClusterNode node, String pattern, String channel, String message) {
                handleExpiredKey(message);
            }
        });
        connection.sync().upstream().commands().psubscribe(EXPIRED_PATTERN);
        clusterConnection = connection;
        clusterResubscribeExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "siga-redis-expiry-resubscribe");
            thread.setDaemon(true);
            return thread;
        });
        log.info("Subscribed to Redis keyspace expiry notifications pattern [{}] on all cluster masters",
                EXPIRED_PATTERN);

        // Lettuce does NOT propagate pub/sub subscriptions to nodes added during a topology
        // change (reference docs: "subscription registrations are not automatically propagated
        // to new nodes added during topology changes, requiring manual subscription management
        // or re-subscription"). When the periodic/adaptive topology refresh — configured via
        // spring.data.redis.lettuce.cluster.refresh.period and
        // spring.data.redis.lettuce.cluster.refresh.adaptive in the deployment's
        // application.properties — detects a master failover, ClusterTopologyChangedEvent fires
        // on the shared event bus; we listen, then re-issue PSUBSCRIBE on the upstream
        // NodeSelection. Lettuce de-dupes PSUBSCRIBE on already-subscribed nodes, so only the
        // freshly-promoted master actually gets the subscription.
        topologyChangeSubscription = client.getResources().eventBus().get()
                .filter(event -> event instanceof ClusterTopologyChangedEvent)
                .subscribe(event -> {
                    log.info("Cluster topology changed; scheduling re-PSUBSCRIBE of keyspace expiry pattern [{}] on all current masters",
                            EXPIRED_PATTERN);
                    scheduleClusterResubscribe();
                }, error -> log.warn("Redis cluster topology event subscription failed; periodic re-PSUBSCRIBE will continue",
                        error));
        // Set only after every lifecycle field above is assigned: readers treat clusterSubscribed
        // as "fully wired", so no lifecycle write may follow this store. Topology events arriving
        // before it are dropped by scheduleClusterResubscribe and covered by the periodic
        // reconciliation below.
        clusterSubscribed = true;
        clusterResubscribeExecutor.scheduleWithFixedDelay(
                () -> resubscribeCluster("periodic reconciliation"),
                CLUSTER_RESUBSCRIBE_INTERVAL.toMillis(),
                CLUSTER_RESUBSCRIBE_INTERVAL.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    private void scheduleClusterResubscribe() {
        ScheduledExecutorService executor = clusterResubscribeExecutor;
        if (!clusterSubscribed || executor == null || executor.isShutdown()) {
            return;
        }
        try {
            executor.execute(() -> resubscribeCluster("topology change"));
        } catch (RuntimeException e) {
            if (clusterSubscribed) {
                log.warn("Failed to schedule Redis keyspace expiry re-PSUBSCRIBE after topology change", e);
            }
        }
    }

    private void resubscribeCluster(String reason) {
        StatefulRedisClusterPubSubConnection<String, String> connection = clusterConnection;
        if (!clusterSubscribed || connection == null) {
            return;
        }
        try {
            AsyncExecutions<Void> executions = connection.async().upstream().commands().psubscribe(EXPIRED_PATTERN);
            executions.asMap().forEach((node, future) -> future.whenComplete((ignored, error) -> {
                if (error == null) {
                    log.debug("Re-PSUBSCRIBE'd Redis keyspace expiry pattern [{}] on [{}] after {}",
                            EXPIRED_PATTERN, describeClusterNode(node), reason);
                } else if (clusterSubscribed) {
                    log.warn("Failed to re-PSUBSCRIBE Redis keyspace expiry pattern [{}] on [{}] after {}; retrying on next topology event or periodic reconciliation",
                            EXPIRED_PATTERN, describeClusterNode(node), reason, error);
                }
            }));
        } catch (RuntimeException e) {
            if (clusterSubscribed) {
                log.warn("Failed to re-PSUBSCRIBE Redis keyspace expiry pattern [{}] after {}; retrying on next topology event or periodic reconciliation",
                        EXPIRED_PATTERN, reason, e);
            }
        }
    }

    private static String describeClusterNode(RedisClusterNode node) {
        return node.getNodeId() + " (" + node.getUri() + ")";
    }

    private void subscribeStandalone(RedisClient client) {
        StatefulRedisPubSubConnection<String, String> connection = client.connectPubSub();
        connection.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String pattern, String channel, String message) {
                handleExpiredKey(message);
            }
        });
        connection.sync().psubscribe(EXPIRED_PATTERN);
        standaloneConnection = connection;
        log.info("Subscribed to Redis keyspace expiry notifications pattern [{}]", EXPIRED_PATTERN);
    }

    private void handleExpiredKey(String expiredKey) {
        if (!RedisSessionKeys.isSessionKey(expiredKey)) {
            return;
        }
        String sessionId = RedisSessionKeys.extractSessionId(expiredKey);
        if (sessionId == null) {
            return;
        }
        log.debug("Session expired, publishing event: sessionId={}", sessionId);
        eventPublisher.publishEvent(new ContainerExpiredEvent(sessionId));
    }

    private void verifyKeyspaceEvents() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            if (connection instanceof RedisClusterConnection cluster) {
                for (var master : cluster.clusterGetNodes()) {
                    if (master.isMaster()) {
                        assertCoversExpiredEvents(
                                cluster.serverCommands().getConfig(master, NOTIFY_KEYSPACE_EVENTS),
                                "cluster master " + master.asString());
                    }
                }
            } else {
                assertCoversExpiredEvents(
                        connection.serverCommands().getConfig(NOTIFY_KEYSPACE_EVENTS),
                        "Redis server");
            }
        }
    }

    private static void assertCoversExpiredEvents(@Nullable Properties config, String source) {
        String value = config == null ? null : config.getProperty(NOTIFY_KEYSPACE_EVENTS);
        if (value == null || value.indexOf('E') < 0
                || (value.indexOf('x') < 0 && value.indexOf('A') < 0)) {
            throw new IllegalStateException("""
                    %s has notify-keyspace-events="%s". SiGa requires the flag set to include \
                    'E' (keyevent prefix) and 'x' (or the 'A' alias that contains x), e.g. \
                    `CONFIG SET notify-keyspace-events Ex`. Without these flags, container expiry \
                    cleanup silently breaks. \
                    Refusing to start to surface the misconfiguration."""
                    .formatted(source, value));
        }
    }

    private static IllegalStateException unsupportedFactory(RedisConnectionFactory factory) {
        return new IllegalStateException("""
                Redis keyspace expiry notifications require LettuceConnectionFactory; got %s. \
                Container expiry events would not be delivered. Refusing to start to surface the \
                unsupported Redis client configuration."""
                .formatted(factory.getClass().getName()));
    }
}
