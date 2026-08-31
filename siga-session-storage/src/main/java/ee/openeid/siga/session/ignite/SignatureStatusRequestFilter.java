package ee.openeid.siga.session.ignite;

import ee.openeid.siga.common.session.ProcessingStatus;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.lang.IgniteBiPredicate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Server-side filter for the {@code SIGNATURE_SESSION} cache {@link org.apache.ignite.cache.query.ScanQuery}
 * issued by {@link IgniteSessionStatusScanner#scanSignatureSessions}. Accepts a container
 * {@code sessionId} when at least one signature session in its map has a
 * {@code processingStatus} of {@code PROCESSING} or {@code EXCEPTION}, a
 * {@code processingStatusTimestamp} older than the configured cutoff, and a
 * {@code processingCounter} still within the retry limit — i.e. it is a candidate for the status
 * reprocessor to re-poll.
 *
 * <p>The predicate runs on Ignite server nodes and reads fields via {@code BinaryObject.field(...)}
 * rather than through the {@code SignatureSession} class. Field-level access on the binary form is
 * provided by {@code SignatureSession}'s {@link org.apache.ignite.binary.Binarylizable}
 * {@code writeBinary}/{@code readBinary} methods; peer-classloaded code cannot classload SiGa
 * domain types on the server, so binary field access is the only path.
 *
 * <p>NB: This class is loaded into Ignite server nodes via peer class loading.
 * If possible, avoid making changes in this class and in its dependencies!
 */
public class SignatureStatusRequestFilter implements IgniteBiPredicate<String, Map<String, BinaryObject>> {
    private final long maxProcessingRetries;
    private final Instant processingTimeout;
    private final Instant exceptionTimeout;

    public SignatureStatusRequestFilter(long maxProcessingRetries, Duration processingTimeout,
                                        Duration exceptionTimeout) {
        this.maxProcessingRetries = maxProcessingRetries;
        this.processingTimeout = Instant.now().minusSeconds(processingTimeout.toSeconds());
        this.exceptionTimeout = Instant.now().minusSeconds(exceptionTimeout.toSeconds());
    }

    @Override
    public boolean apply(String containerSessionId, Map<String, BinaryObject> signatureSessions) {
        return signatureSessions.values().stream()
                .map(s -> (BinaryObject) s.field("sessionStatus"))
                .anyMatch(sessionStatus -> {
                    int statusOrdinal = sessionStatus.<BinaryObject>field("processingStatus").enumOrdinal();
                    ProcessingStatus processingStatus = ProcessingStatus
                            .values()[statusOrdinal];
                    Instant statusTimestamp = sessionStatus.field("processingStatusTimestamp");
                    int processingCounter = sessionStatus.field("processingCounter");
                    return isApplyFilter(this, processingStatus, statusTimestamp, processingCounter);
                });
    }

    public static boolean isApplyFilter(SignatureStatusRequestFilter filter, ProcessingStatus processingStatus,
                                        Instant statusTimestamp,
                                        int processingCounter) {
        boolean isProcessingTimeout = ProcessingStatus.PROCESSING == processingStatus
                && statusTimestamp.isBefore(filter.processingTimeout);
        boolean isExceptionTimeout = ProcessingStatus.EXCEPTION == processingStatus
                && statusTimestamp.isBefore(filter.exceptionTimeout);
        return (isProcessingTimeout || isExceptionTimeout) && processingCounter <= filter.maxProcessingRetries;
    }
}
