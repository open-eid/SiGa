package ee.openeid.siga.session.configuration;

import jakarta.validation.constraints.AssertTrue;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Binds to the subset of {@code spring.data.redis} properties that Lettuce's cluster topology
 * refresh depends on, and fails the Spring context at binding time if Redis cluster mode is
 * active but the refresh settings aren't configured.
 *
 * <p>Lettuce's defaults leave periodic refresh OFF and adaptive triggers EMPTY, so without
 * explicit values Lettuce never publishes {@code ClusterTopologyChangedEvent} on failover and
 * {@code RedisSessionExpiryNotifier}'s pub/sub subscription does not follow a promoted master —
 * container expiry cleanup silently degrades to a 5s polling fallback. This guard surfaces that
 * misconfiguration at startup instead of in production after a failover.
 *
 * <p>Binds to the same prefix as Spring Boot's {@code DataRedisProperties}; both beans receive
 * the same values independently, only this one runs the validations.
 */
@Validated
@ConfigurationProperties(prefix = "spring.data.redis")
public record RedisClusterTopologyRefreshValidation(
        @Nullable Cluster cluster,
        @Nullable Lettuce lettuce) {

    public record Cluster(@Nullable List<String> nodes) {
    }

    public record Lettuce(@Nullable LettuceCluster cluster) {
        public record LettuceCluster(@Nullable Refresh refresh) {
            public record Refresh(
                    @Nullable Duration period,
                    boolean adaptive,
                    boolean dynamicRefreshSources) {
            }
        }
    }

    @AssertTrue(message = "spring.data.redis.lettuce.cluster.refresh.period must be set "
            + "(e.g. 30s) when spring.data.redis.cluster.nodes is set, so Lettuce runs periodic "
            + "topology refresh and RedisSessionExpiryNotifier follows pub/sub to a promoted master")
    public boolean isPeriodicRefreshConfigured() {
        if (clusterModeInactive()) {
            return true;
        }
        Lettuce.LettuceCluster.Refresh refresh = refresh();
        return refresh != null && refresh.period() != null;
    }

    @AssertTrue(message = "spring.data.redis.lettuce.cluster.refresh.period must be at least 1s — "
            + "anything shorter hammers every cluster node with CLUSTER NODES traffic and does not "
            + "buy meaningfully faster failover detection (adaptive triggers already react to "
            + "MOVED/ASK redirects without waiting for the next periodic tick)")
    public boolean isPeriodAtLeastOneSecond() {
        Lettuce.LettuceCluster.Refresh refresh = refresh();
        Duration period = refresh != null ? refresh.period() : null;
        // Defer the "missing entirely" case to isPeriodicRefreshConfigured so a null period
        // produces exactly one violation instead of two.
        return period == null || period.compareTo(Duration.ofSeconds(1)) >= 0;
    }

    @AssertTrue(message = "spring.data.redis.lettuce.cluster.refresh.adaptive must be true when "
            + "spring.data.redis.cluster.nodes is set, so Lettuce enables MOVED/ASK/"
            + "PERSISTENT_RECONNECTS/UNCOVERED_SLOT/UNKNOWN_NODE adaptive topology triggers")
    public boolean isAdaptiveRefreshEnabled() {
        if (clusterModeInactive()) {
            return true;
        }
        Lettuce.LettuceCluster.Refresh refresh = refresh();
        return refresh != null && refresh.adaptive();
    }

    @AssertTrue(message = "spring.data.redis.lettuce.cluster.refresh.dynamic-refresh-sources must "
            + "be true when spring.data.redis.cluster.nodes is set, so Lettuce queries every "
            + "cluster node for topology rather than only the initial seeds")
    public boolean isDynamicRefreshSourcesEnabled() {
        if (clusterModeInactive()) {
            return true;
        }
        Lettuce.LettuceCluster.Refresh refresh = refresh();
        return refresh != null && refresh.dynamicRefreshSources();
    }

    private boolean clusterModeInactive() {
        return cluster == null || cluster.nodes() == null || cluster.nodes().isEmpty();
    }

    private Lettuce.LettuceCluster.@Nullable Refresh refresh() {
        if (lettuce == null || lettuce.cluster() == null) {
            return null;
        }
        return lettuce.cluster().refresh();
    }
}
