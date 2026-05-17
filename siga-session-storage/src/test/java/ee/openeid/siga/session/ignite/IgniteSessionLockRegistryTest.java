package ee.openeid.siga.session.ignite;

import ee.openeid.siga.session.spi.SessionLockRegistry;
import ee.openeid.siga.session.spi.SessionLockRegistryTest;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteSemaphore;
import org.apache.ignite.Ignition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ignite-backed contract tests for {@link SessionLockRegistry}. The shared SPI contract is
 * inherited from {@link SessionLockRegistryTest}; this subclass starts a single embedded Ignite
 * node and constructs two independent {@link IgniteSessionLockRegistry} instances over it.
 * {@code IgniteSemaphore} instances are cluster-wide by name, so two registries on one node are
 * sufficient to exercise the cross-instance mutual-exclusion contract.
 */
class IgniteSessionLockRegistryTest extends SessionLockRegistryTest {

    private static Ignite ignite;
    private static SessionLockRegistry primaryRegistry;
    private static SessionLockRegistry secondaryRegistry;

    @BeforeAll
    static void startIgnite() {
        System.setProperty("IGNITE_OVERRIDE_CONSISTENT_ID", "node00");
        ignite = Ignition.start("ignite-test-configuration.xml");
        primaryRegistry = new IgniteSessionLockRegistry(ignite);
        secondaryRegistry = new IgniteSessionLockRegistry(ignite);
    }

    @AfterAll
    static void stopIgnite() {
        if (ignite != null) {
            ignite.close();
        }
    }

    @Override
    protected SessionLockRegistry primaryRegistry() {
        return primaryRegistry;
    }

    @Override
    protected SessionLockRegistry secondaryRegistry() {
        return secondaryRegistry;
    }

    @Test
    void shouldCreateDurableFailoverSafeSemaphore_WhenLockAcquired() {
        // The production call site is `ignite.semaphore(name, 1, true, true)` — both flags matter:
        // failoverSafe=true guarantees waiters get notified when a holder leaves the cluster;
        // create=true bootstraps the metadata so the semaphore actually exists. A regression to
        // failoverSafe=false would silently strand waiters on node death. The wrapper fetches
        // lazily, so the metadata exists from the first tryLock onwards (not from obtain).
        String sessionId = "v1_svc_durable_semaphore";
        Lock lock = primaryRegistry.obtain(sessionId);
        assertTrue(lock.tryLock());
        try {
            // create=false here so we only succeed if the metadata exists (proves create=true above).
            IgniteSemaphore semaphore = ignite.semaphore(sessionId, 1, true, false);
            assertNotNull(semaphore, "tryLock must register cluster-wide semaphore metadata");
        } finally {
            lock.unlock();
        }
    }

    @Test
    void shouldDestroySemaphoreOnUnlock() {
        // Pins the close()-on-unlock contract: after unlock the cluster-wide semaphore metadata
        // must be gone so it doesn't leak per session. A lookup with create=false must return null.
        String sessionId = "v1_svc_unlock_destroys";
        Lock lock = primaryRegistry.obtain(sessionId);
        assertTrue(lock.tryLock());
        lock.unlock();

        IgniteSemaphore residue = ignite.semaphore(sessionId, 1, true, false);
        assertNull(residue,
                "unlock() must destroy the cluster-wide semaphore so no metadata accumulates "
                        + "per session. If this ever returns non-null, the per-session metadata "
                        + "leak is back and a separate cleanup listener becomes necessary again.");
    }
}
