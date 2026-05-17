package ee.openeid.siga.session.ignite;

import ee.openeid.siga.common.session.ProcessingStatus;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.internal.binary.BinaryEnumObjectImpl;
import org.apache.ignite.lang.IgniteBiPredicate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Server-side filter for the {@code CERTIFICATE_SESSION} cache {@link org.apache.ignite.cache.query.ScanQuery}
 * issued by {@link IgniteSessionStatusScanner#scanCertificateSessions}. Accepts a container
 * {@code sessionId} when at least one certificate session in its map has a
 * {@code processingStatus} of {@code PROCESSING} or {@code EXCEPTION}, a
 * {@code processingStatusTimestamp} older than the configured cutoff, and a
 * {@code processingCounter} still within the retry limit — i.e. it is a candidate for the status
 * reprocessor to re-poll.
 *
 * <p>The predicate runs on Ignite server nodes and reads fields via {@code BinaryObject.field(...)}
 * rather than through the {@code CertificateSession} class. {@code CertificateSession} has no
 * custom {@link org.apache.ignite.binary.BinarySerializer} (unlike {@code SignatureSession}) — its
 * fields are simple enough for Ignite's default reflective marshaller to expose on the binary form,
 * which is sufficient for the field accesses performed here.
 *
 * <p>NB: This class is loaded into Ignite server nodes via peer class loading.
 * If possible, avoid making changes in this class and in its dependencies!
 */
public class CertificateStatusRequestFilter implements IgniteBiPredicate<String, Map<String, BinaryObject>> {
    private final long maxProcessingRetries;
    private final Instant processingTimeout;
    private final Instant exceptionTimeout;

    public CertificateStatusRequestFilter(long maxProcessingRetries, Duration processingTimeout,
                                          Duration exceptionTimeout) {
        this.maxProcessingRetries = maxProcessingRetries;
        this.processingTimeout = Instant.now().minusSeconds(processingTimeout.toSeconds());
        this.exceptionTimeout = Instant.now().minusSeconds(exceptionTimeout.toSeconds());
    }

    @Override
    public boolean apply(String containerSessionId, Map<String, BinaryObject> certificateSessions) {
        return certificateSessions.values().stream()
                .map(s -> (BinaryObject) s.field("sessionStatus"))
                .anyMatch(sessionStatus -> {
                    int statusOrdinal = sessionStatus.<BinaryEnumObjectImpl>field("processingStatus").enumOrdinal();
                    ProcessingStatus processingStatus = ProcessingStatus
                            .values()[statusOrdinal];
                    Instant statusTimestamp = sessionStatus.field("processingStatusTimestamp");
                    int processingCounter = sessionStatus.field("processingCounter");
                    return isApplyFilter(this, processingStatus, statusTimestamp, processingCounter);
                });
    }

    public static boolean isApplyFilter(CertificateStatusRequestFilter filter, ProcessingStatus processingStatus,
                                        Instant statusTimestamp,
                                        int processingCounter) {
        boolean isProcessingTimeout = ProcessingStatus.PROCESSING == processingStatus
                && statusTimestamp.isBefore(filter.processingTimeout);
        boolean isExceptionTimeout = ProcessingStatus.EXCEPTION == processingStatus
                && statusTimestamp.isBefore(filter.exceptionTimeout);
        return (isProcessingTimeout || isExceptionTimeout) && processingCounter <= filter.maxProcessingRetries;
    }
}
