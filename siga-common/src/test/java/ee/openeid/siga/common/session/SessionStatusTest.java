package ee.openeid.siga.common.session;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionStatusTest {

    private static final Instant TIMESTAMP = Instant.parse("2026-05-18T10:00:00Z");
    private static final Instant PROCESSING_CUTOFF = TIMESTAMP.plusSeconds(60);
    private static final Instant EXCEPTION_CUTOFF = TIMESTAMP.plusSeconds(300);
    private static final long MAX_RETRIES = 5L;

    @Test
    void isPendingReprocessing_acceptsProcessingWithinBudget() {
        assertTrue(status(ProcessingStatus.PROCESSING, TIMESTAMP, 0).isPendingReprocessing(MAX_RETRIES));
        assertTrue(status(ProcessingStatus.PROCESSING, TIMESTAMP, (int) MAX_RETRIES).isPendingReprocessing(MAX_RETRIES));
    }

    @Test
    void isPendingReprocessing_acceptsExceptionWithinBudget() {
        assertTrue(status(ProcessingStatus.EXCEPTION, TIMESTAMP, 0).isPendingReprocessing(MAX_RETRIES));
    }

    @Test
    void isPendingReprocessing_rejectsCounterAboveBudget() {
        assertFalse(status(ProcessingStatus.PROCESSING, TIMESTAMP, (int) MAX_RETRIES + 1).isPendingReprocessing(MAX_RETRIES));
    }

    @Test
    void isPendingReprocessing_rejectsTerminalStatus() {
        assertFalse(status(ProcessingStatus.RESULT, TIMESTAMP, 0).isPendingReprocessing(MAX_RETRIES));
    }

    @Test
    void isPendingReprocessing_rejectsNullTimestamp() {
        SessionStatus status = SessionStatus.builder()
                .processingStatus(ProcessingStatus.PROCESSING)
                .processingCounter(0)
                .processingStatusTimestamp(null)
                .build();
        assertFalse(status.isPendingReprocessing(MAX_RETRIES));
    }

    @Test
    void isDueForReprocessing_processingPastCutoff() {
        assertTrue(status(ProcessingStatus.PROCESSING, TIMESTAMP, 0)
                .isDueForReprocessing(PROCESSING_CUTOFF, EXCEPTION_CUTOFF, MAX_RETRIES));
    }

    @Test
    void isDueForReprocessing_processingAtCutoffNotYetDue() {
        // isBefore() is strict — equality is not past the cutoff
        assertFalse(status(ProcessingStatus.PROCESSING, TIMESTAMP, 0)
                .isDueForReprocessing(TIMESTAMP, EXCEPTION_CUTOFF, MAX_RETRIES));
    }

    @Test
    void isDueForReprocessing_exceptionUsesExceptionCutoff() {
        // Exception timestamp is past EXCEPTION_CUTOFF only when the cutoff is after it; here we
        // place the cutoff after the timestamp so the entry is due.
        assertTrue(status(ProcessingStatus.EXCEPTION, TIMESTAMP, 0)
                .isDueForReprocessing(PROCESSING_CUTOFF, EXCEPTION_CUTOFF, MAX_RETRIES));
        // Pin that PROCESSING_CUTOFF is ignored for EXCEPTION status: tighten exceptionCutoff to
        // the timestamp itself and the entry is no longer due, even though processingCutoff is past it.
        assertFalse(status(ProcessingStatus.EXCEPTION, TIMESTAMP, 0)
                .isDueForReprocessing(PROCESSING_CUTOFF, TIMESTAMP, MAX_RETRIES));
    }

    @Test
    void isDueForReprocessing_rejectsWhenCounterAboveBudget() {
        assertFalse(status(ProcessingStatus.PROCESSING, TIMESTAMP, (int) MAX_RETRIES + 1)
                .isDueForReprocessing(PROCESSING_CUTOFF, EXCEPTION_CUTOFF, MAX_RETRIES));
    }

    @Test
    void isDueForReprocessing_rejectsTerminalStatus() {
        assertFalse(status(ProcessingStatus.RESULT, TIMESTAMP, 0)
                .isDueForReprocessing(PROCESSING_CUTOFF, EXCEPTION_CUTOFF, MAX_RETRIES));
    }

    private static SessionStatus status(ProcessingStatus processingStatus, Instant timestamp, int counter) {
        return SessionStatus.builder()
                .processingStatus(processingStatus)
                .processingStatusTimestamp(timestamp)
                .processingCounter(counter)
                .build();
    }
}
