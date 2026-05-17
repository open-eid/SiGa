package ee.openeid.siga.session.spi;

import org.springframework.integration.support.locks.LockRegistry;

import java.util.concurrent.locks.Lock;

/**
 * Cross-backend distributed lock registry used by MID/SID signing delegates and the status
 * reprocessor to serialize writes against a single container session or signature.
 *
 * <p>Extends Spring Integration's {@link LockRegistry} parameterized with {@link Lock}, which lets
 * implementations reuse Spring Integration's own {@code RedisLockRegistry}, {@code JdbcLockRegistry}
 * or a custom adapter over {@code IgniteSemaphore} with no friction.
 *
 * <p>Implementations must be:
 * <ul>
 *   <li><b>Cluster-wide</b> — two nodes obtaining the same lock key must observe mutual exclusion.</li>
 *   <li><b>Failover-safe</b> — a node dying while holding the lock must not block the cluster
 *       indefinitely (Ignite: {@code failoverSafe=true}; Redis: TTL-based auto-release).</li>
 *   <li><b>Thread-safe</b> — {@link #obtain(Object)} can be called concurrently from any thread.</li>
 * </ul>
 *
 * <p>Conditions are not supported — the returned {@link Lock#newCondition()} is expected to throw
 * {@link UnsupportedOperationException}.
 */
public interface SessionLockRegistry extends LockRegistry<Lock> {
}
