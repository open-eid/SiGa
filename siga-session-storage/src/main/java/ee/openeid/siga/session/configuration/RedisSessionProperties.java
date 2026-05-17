package ee.openeid.siga.session.configuration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Redis session-storage tuning.
 *
 * @param sessionTtl                     TTL applied to the container, signature and certificate session payload keys.
 * @param lockTtl                        Crash-recovery lease for distributed locks obtained via
 *                                       {@code SessionLockRegistry}.
 *
 *                                       <p>While the owning JVM is alive, Spring Integration's {@code RedisLockRegistry}
 *                                       renews the underlying Redis key every {@code lockTtl/3} via the
 *                                       {@code redisLockRenewalTaskScheduler} wired in {@code RedisSessionConfiguration},
 *                                       so this value bounds the time another node must wait before claiming a lock
 *                                       abandoned by a dead JVM — not the maximum work duration of a live holder.
 *
 *                                       <p>Trade-off: too short → a transient Redis outage exceeding the lease transfers
 *                                       ownership to another node mid-work. Too long → stalled recovery for in-flight
 *                                       signatures after a crash. Must stay strictly below {@code sessionTtl} so stale
 *                                       locks cannot outlive the session payload they protect. Renewal failures are
 *                                       logged at WARN by the renewal task scheduler.
 * @param statusScanBatchSize            Per-tick cap on the number of session candidates the Redis due-queue
 *                                       scanner pulls from each {@code siga:reprocess:*} ZSET via
 *                                       {@code ZRANGEBYSCORE … LIMIT}. With the reprocessing scheduler firing every 5s,
 *                                       the default 100 yields ~1200 sessions/min throughput while bounding the per-tick
 *                                       fan-out: a backlog of stuck signatures cannot stall the scheduler thread on a
 *                                       single oversized result set.
 * @param lockRenewalThreadPoolSize      Pool size for {@code redisLockRenewalTaskScheduler}. Every live
 *                                       SiGa lock schedules a renewal task at {@code lockTtl}/3; the pool must be wide
 *                                       enough that a single slow Redis call doesn't head-of-line-block renewals for
 *                                       unrelated locks.
 * @param skipKeyspaceEventsVerification Skip the {@code CONFIG GET notify-keyspace-events} probe
 *                                       that {@link ee.openeid.siga.session.redis.RedisSessionExpiryNotifier} runs at
 *                                       startup.
 *
 *                                       <p>Managed Valkey services (AWS ElastiCache, MemoryDB) block {@code CONFIG} at the
 *                                       engine layer regardless of ACL, so the probe always errors with
 *                                       {@code ERR unknown command 'CONFIG'} and the bean fails to initialise — even when
 *                                       the parameter group has {@code notify-keyspace-events=Ex} set correctly. Setting
 *                                       this to {@code true} makes the notifier log a warning, skip the verification, and
 *                                       proceed with the pub/sub subscription; operators are then on the hook to confirm
 *                                       the parameter group themselves.
 *
 *                                       <p>Leave at {@code false} for self-hosted deployments — the fail-fast guard
 *                                       catches the silent-cleanup-broken misconfiguration before any sessions get
 *                                       written.
 */
@Validated
@ConfigurationProperties(prefix = "siga.session-storage.redis")
public record RedisSessionProperties(
        @DefaultValue("300s") @NotNull Duration sessionTtl,
        @DefaultValue("120s") @NotNull Duration lockTtl,
        @DefaultValue("100") @Positive int statusScanBatchSize,
        @DefaultValue("32") @Positive int lockRenewalThreadPoolSize,
        @DefaultValue("false") boolean skipKeyspaceEventsVerification) {

    /**
     * Validates that {@link #lockTtl} stays strictly below {@link #sessionTtl}. Spring's
     * {@code @Validated} binding fails fast at context startup when the constraint is violated,
     * mirroring the invariant documented on {@link #lockTtl}.
     */
    @AssertTrue(message = "lockTtl must be strictly less than sessionTtl so stale locks "
            + "cannot outlive the session payload they protect")
    public boolean isLockTtlBelowSessionTtl() {
        return lockTtl.compareTo(sessionTtl) < 0;
    }

    /**
     * Instance populated with the same values Spring binds when no
     * {@code siga.session-storage.redis.*} properties are set. Intended for tests that exercise the
     * default configuration.
     */
    public static RedisSessionProperties withDefaults() {
        return new RedisSessionProperties(Duration.ofSeconds(300), Duration.ofSeconds(120), 100, 32, false);
    }
}
