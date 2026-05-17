package ee.openeid.siga.session.spi;

import org.junit.jupiter.api.Test;

import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Backend-agnostic contract tests for {@link SessionLockRegistry}. Concrete subclasses wire a
 * real backend (Redis Testcontainer, embedded Ignite) and provide two independent registry
 * instances pointing at the same cluster-wide lock namespace, so {@link #shouldRejectLock_WhenAlreadyHeldByAnotherRegistry()}
 * actually exercises distributed mutual exclusion rather than same-instance reentrance.
 */
public abstract class SessionLockRegistryTest {

    protected abstract SessionLockRegistry primaryRegistry();

    protected abstract SessionLockRegistry secondaryRegistry();

    @Test
    void shouldAcquireLock_WhenLockIsFree() {
        Lock lock = primaryRegistry().obtain("session-acquire");
        assertTrue(lock.tryLock());
        assertDoesNotThrow(lock::unlock);
    }

    @Test
    void shouldRejectLock_WhenAlreadyHeldByAnotherRegistry() {
        Lock first = primaryRegistry().obtain("session-mutex");
        Lock second = secondaryRegistry().obtain("session-mutex");

        assertTrue(first.tryLock());
        try {
            assertFalse(second.tryLock(),
                    "Second registry must observe the lock as held by the first");
        } finally {
            first.unlock();
        }

        assertTrue(second.tryLock(),
                "After release, second registry must be able to acquire the same key");
        second.unlock();
    }

    @Test
    void shouldAllowReacquire_WhenUnlocked() {
        Lock lock = primaryRegistry().obtain("session-release");
        assertTrue(lock.tryLock());
        lock.unlock();

        Lock reacquired = primaryRegistry().obtain("session-release");
        assertTrue(reacquired.tryLock());
        reacquired.unlock();
    }
}
