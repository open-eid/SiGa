package ee.openeid.siga.service.signature.container.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.internal.binary.BinaryEnumObjectImpl;
import org.apache.ignite.lang.IgniteBiPredicate;

import ee.openeid.siga.common.session.ProcessingStatus;

/**
 * NB: This class is loaded into Ignite server nodes via peer class loading.
 * If possible, avoid making changes in this class and in its dependencies!
 */
public class CertificateStatusRequestFilter implements IgniteBiPredicate<String, Map<String, BinaryObject>> {
    private final long maxProcessingRetries;
    private final LocalDateTime processingTimeout;
    private final LocalDateTime exceptionTimeout;

    public CertificateStatusRequestFilter(long maxProcessingRetries, Duration processingTimeout,
            Duration exceptionTimeout) {
        this.maxProcessingRetries = maxProcessingRetries;
        this.processingTimeout = LocalDateTime.now().minusSeconds(processingTimeout.toSeconds());
        this.exceptionTimeout = LocalDateTime.now().minusSeconds(exceptionTimeout.toSeconds());
    }

    @Override
    public boolean apply(String containerSessionId, Map<String, BinaryObject> certificateSessions) {
        return certificateSessions.values().stream()
                .map(s -> (BinaryObject) s.field("sessionStatus"))
                .anyMatch(sessionStatus -> {
                    int statusOrdinal = sessionStatus.<BinaryEnumObjectImpl>field("processingStatus").enumOrdinal();
                    ProcessingStatus processingStatus = ProcessingStatus
                            .values()[statusOrdinal];
                    LocalDateTime statusTimestamp = sessionStatus.field("processingStatusTimestamp");
                    int processingCounter = sessionStatus.field("processingCounter");
                    return isApplyFilter(this, processingStatus, statusTimestamp, processingCounter);
                });
    }

    static boolean isApplyFilter(CertificateStatusRequestFilter filter, ProcessingStatus processingStatus,
            LocalDateTime statusTimestamp,
            int processingCounter) {
        boolean isProcessingTimeout = ProcessingStatus.PROCESSING == processingStatus
                && statusTimestamp.isBefore(filter.processingTimeout);
        boolean isExceptionTimeout = ProcessingStatus.EXCEPTION == processingStatus
                && statusTimestamp.isBefore(filter.exceptionTimeout);
        return (isProcessingTimeout || isExceptionTimeout) && processingCounter <= filter.maxProcessingRetries;
    }
}
