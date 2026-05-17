package ee.openeid.siga.session.ignite;

import ee.openeid.siga.session.spi.SessionLockRegistry;
import lombok.RequiredArgsConstructor;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.IgniteInterruptedException;
import org.apache.ignite.IgniteSemaphore;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * Ignite-backed {@link SessionLockRegistry}. {@code unlock()} destroys the cluster-wide
 * {@link IgniteSemaphore} via {@code close()} rather than just releasing a permit; the wrapper
 * re-fetches the semaphore on every operation via {@code ignite.semaphore(name, 1, true, true)}
 * (atomic get-or-create), so callers still observe standard reusable-{@link Lock} behaviour.
 *
 * <p>The deviation from a literal reusable-Lock implementation is deliberate. It preserves the
 * pre-SPI Ignite behaviour (the signing delegates used {@code try (semaphore) { ... }} with
 * {@code tryAcquire}, destroying and recreating the cluster-wide primitive per attempt) and avoids
 * the per-session metadata leak that {@code release()}-only semantics produce — a leak that would
 * otherwise require a separate expiry-driven cleanup listener. SIGA's actual call sites all go
 * through {@code SessionLocks.tryRun(...)} (one-shot {@code obtain → tryLock → work → unlock}), so
 * no caller relies on a {@link Lock} instance surviving its own {@code unlock()}.
 *
 * <p>This is asymmetric with the Redis backend: Spring Integration's {@code RedisLockRegistry}
 * uses reusable TTL-keyed locks and cannot mirror destroy-and-recreate. Observable behaviour
 * ("only one holder per key at any instant; holder is released on JVM death") is identical across
 * backends; only the mechanism differs.
 *
 * <p>Concurrency: a concurrent {@code close()} on another node can make a freshly fetched
 * {@link IgniteSemaphore} reference become stale between the lookup and the {@code tryAcquire}.
 * The {@code tryLock} paths catch {@link IgniteException} and report "not acquired"; callers via
 * {@code SessionLocks.tryRun} treat that as "skip, reprocessor retries", which mirrors the
 * existing tolerance for the Redis {@code ConcurrentModificationException} ownership-loss case.
 *
 * <p>Residual orphan window: only {@code close()} ever removes the semaphore's entry from
 * Ignite's internal atomics system cache. A JVM that dies between create/acquire and
 * {@code unlock()} has its permits restored by failover-safety, but the entry itself stays
 * behind. This normally self-heals because the semaphore is keyed by session/signature id: the
 * next {@code tryLock} for the same id — typically {@code SessionStatusReprocessingService}
 * retrying the interrupted work — get-or-creates that same entry, and that cycle's
 * {@code unlock()} destroys it. An orphan is permanent only if the key is never locked again:
 * (a) the holder persisted the final session status and died just before {@code close()}, so
 * nothing ever retries that session; or (b) {@code tryAcquire} threw a non-"removed"
 * {@link IgniteException} right after this node created the entry (returned {@code false}, so no
 * {@code unlock()} follows), and the session then expired before any further lock attempt. Each
 * such entry is a few hundred bytes of non-persistent cluster memory (gone on full-cluster
 * restart) with its full permit count intact, so mutual exclusion is unaffected. Deliberately
 * left unfixed: closing a semaphore that was not acquired would destroy it under the current
 * holder — a hazard the pre-SPI container-level {@code try (semaphore)} path actually had — and
 * expiry-driven reaping costs more than the leak.
 */
@RequiredArgsConstructor
public class IgniteSessionLockRegistry implements SessionLockRegistry {
    private final Ignite ignite;

    @Override
    public Lock obtain(Object lockKey) {
        return new IgniteSemaphoreLock(ignite, lockKey.toString());
    }

    private record IgniteSemaphoreLock(Ignite ignite, String name) implements Lock {

        @Override
        public void lock() {
            throw new UnsupportedOperationException("IgniteSemaphore-backed lock does not support lock()");
        }

        @Override
        public void lockInterruptibly() {
            throw new UnsupportedOperationException("IgniteSemaphore-backed lock does not support lockInterruptibly()");
        }

        @SuppressWarnings("resource")
        @Override
        public boolean tryLock() {
            try {
                return semaphore().tryAcquire();
            } catch (IgniteException e) {
                return false;
            }
        }

        @SuppressWarnings("resource")
        @Override
        public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
            try {
                return semaphore().tryAcquire(timeout, unit);
            } catch (IgniteInterruptedException e) {
                Thread.currentThread().interrupt();
                throw asInterrupted(e);
            } catch (IgniteException e) {
                return false;
            }
        }

        @Override
        public void unlock() {
            IgniteSemaphore existing = ignite.semaphore(name, 1, true, false);
            if (existing == null) {
                return;
            }
            try {
                existing.close();
            } catch (IgniteException ignored) {
                // Already destroyed by a concurrent unlock from another node — benign.
            }
        }

        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException("IgniteSemaphore-backed lock does not support Condition");
        }

        private IgniteSemaphore semaphore() {
            return ignite.semaphore(name, 1, true, true);
        }

        private static InterruptedException asInterrupted(IgniteInterruptedException cause) {
            InterruptedException wrapped = new InterruptedException(cause.getMessage());
            wrapped.initCause(cause);
            return wrapped;
        }
    }
}
