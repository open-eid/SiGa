package ee.openeid.siga.session.spi;

import lombok.extern.slf4j.Slf4j;

import java.util.ConcurrentModificationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * Utility helpers for running work under a {@link Lock} obtained from {@link SessionLockRegistry}.
 *
 * <p>The helpers implement a "try, don't wait forever" contract — they never throw an
 * {@link InterruptedException} to the caller (it is restored on the thread and reported as
 * {@code false}). Preserves the MID/SID signing delegate semantics: if the lock can't be acquired
 * the work is skipped silently and {@code SessionStatusReprocessingService} will retry later.
 *
 * <p>The return value reports BOTH "could not acquire" and "lost ownership mid-work" as
 * {@code false}. Spring Integration provides no first-class lost-ownership callback on
 * {@code RedisLockRegistry}; the canonical signal is the {@link ConcurrentModificationException}
 * Spring Integration throws from {@code Lock.unlock()} when the local owner UUID no longer
 * matches the value in Redis (renewal stopped, lease lapsed, or a peer JVM acquired the same
 * lock after the Redis-side key disappeared). Callers that already retry on {@code false}
 * therefore also retry on split-brain — and because the peer JVM's run already advanced the
 * persistent session state, the retry observes the work as done and short-circuits.
 */
@Slf4j
public final class SessionLocks {

    private SessionLocks() {
    }

    public static boolean tryRun(Lock lock, Runnable work) {
        if (!lock.tryLock()) {
            return false;
        }
        return runAndUnlock(lock, work);
    }

    public static boolean tryRun(Lock lock, long timeout, TimeUnit unit, Runnable work) {
        boolean acquired;
        try {
            acquired = lock.tryLock(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        if (!acquired) {
            return false;
        }
        return runAndUnlock(lock, work);
    }

    private static boolean runAndUnlock(Lock lock, Runnable work) {
        boolean ownershipRetained = true;
        try {
            work.run();
        } finally {
            try {
                lock.unlock();
            } catch (ConcurrentModificationException e) {
                // Spring Integration's RedisLockRegistry throws this when the local lock's owner
                // UUID no longer matches the value in Redis — i.e., renewal stopped or the lease
                // lapsed mid-work and another node has taken the key. The Redis-side lock is
                // already gone, so nothing to release; reporting false to the caller treats this
                // the same as "couldn't acquire" so existing retry-on-skip code paths converge.
                log.warn("Session lock ownership was lost before unlock — work ran without exclusive ownership: {}",
                        e.getMessage());
                ownershipRetained = false;
            }
        }
        return ownershipRetained;
    }
}
