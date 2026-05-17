package ee.openeid.siga.session.spi;

import org.junit.jupiter.api.Test;

import java.util.ConcurrentModificationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionLocksTest {

    @Test
    void shouldRunWorkAndReleaseLock_WhenImmediateAcquisitionSucceeds() {
        Lock lock = mock(Lock.class);
        when(lock.tryLock()).thenReturn(true);
        AtomicBoolean ran = new AtomicBoolean();

        boolean executed = SessionLocks.tryRun(lock, () -> ran.set(true));

        assertTrue(executed);
        assertTrue(ran.get());
        verify(lock).tryLock();
        verify(lock).unlock();
    }

    @Test
    void shouldSkipWork_WhenImmediateAcquisitionFails() {
        Lock lock = mock(Lock.class);
        when(lock.tryLock()).thenReturn(false);
        AtomicBoolean ran = new AtomicBoolean();

        boolean executed = SessionLocks.tryRun(lock, () -> ran.set(true));

        assertFalse(executed);
        assertFalse(ran.get());
        verify(lock).tryLock();
        verify(lock, never()).unlock();
    }

    @Test
    void shouldReleaseLock_WhenWorkThrows() {
        Lock lock = mock(Lock.class);
        when(lock.tryLock()).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> SessionLocks.tryRun(lock, () -> {
            throw new IllegalStateException("boom");
        }));

        verify(lock).unlock();
    }

    @Test
    void shouldRunWorkAndReleaseLock_WhenTimedAcquisitionSucceeds() throws InterruptedException {
        Lock lock = mock(Lock.class);
        when(lock.tryLock(5L, TimeUnit.SECONDS)).thenReturn(true);
        AtomicBoolean ran = new AtomicBoolean();

        boolean executed = SessionLocks.tryRun(lock, 5L, TimeUnit.SECONDS, () -> ran.set(true));

        assertTrue(executed);
        assertTrue(ran.get());
        verify(lock).tryLock(5L, TimeUnit.SECONDS);
        verify(lock).unlock();
    }

    @Test
    void shouldSkipWork_WhenTimedAcquisitionTimesOut() throws InterruptedException {
        Lock lock = mock(Lock.class);
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(false);
        AtomicBoolean ran = new AtomicBoolean();

        boolean executed = SessionLocks.tryRun(lock, 1L, TimeUnit.SECONDS, () -> ran.set(true));

        assertFalse(executed);
        assertFalse(ran.get());
        verify(lock, never()).unlock();
    }

    @Test
    void shouldRestoreInterruptFlagAndSkipWork_WhenTimedAcquisitionInterrupted() throws InterruptedException {
        Lock lock = mock(Lock.class);
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException("interrupted"));
        AtomicBoolean ran = new AtomicBoolean();

        try {
            boolean executed = SessionLocks.tryRun(lock, 1L, TimeUnit.SECONDS, () -> ran.set(true));

            assertFalse(executed);
            assertFalse(ran.get());
            assertTrue(Thread.currentThread().isInterrupted(),
                    "Interrupt flag must be restored after swallowing InterruptedException");
            verify(lock, never()).unlock();
        } finally {
            // Clear the interrupt flag so it doesn't leak into subsequent tests.
            Thread.interrupted();
        }
    }

    @Test
    void shouldReturnFalseAndKeepWorkRan_WhenUnlockReportsLostOwnership() {
        // When unlock reports that the local owner no longer matches Redis, the work has already
        // run but exclusive ownership was not retained. The helper reports false so callers treat
        // the run like a skipped attempt and can retry later.
        Lock lock = mock(Lock.class);
        when(lock.tryLock()).thenReturn(true);
        doThrow(new ConcurrentModificationException("lock lease lapsed")).when(lock).unlock();
        AtomicBoolean ran = new AtomicBoolean();

        boolean executed = SessionLocks.tryRun(lock, () -> ran.set(true));

        assertFalse(executed);
        assertTrue(ran.get(), "work must still have executed before the CME surfaced");
        verify(lock).unlock();
    }

    @Test
    void shouldNotMaskWorkException_WhenUnlockAlsoFails() {
        Lock lock = mock(Lock.class);
        when(lock.tryLock()).thenReturn(true);
        doThrow(new ConcurrentModificationException("lock lease lapsed")).when(lock).unlock();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> SessionLocks.tryRun(lock, () -> {
                    throw new IllegalStateException("work failed");
                }));

        assertTrue(thrown.getMessage().contains("work failed"));
        verify(lock).unlock();
    }
}
