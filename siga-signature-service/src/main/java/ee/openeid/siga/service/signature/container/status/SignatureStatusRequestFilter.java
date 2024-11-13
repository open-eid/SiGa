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
public class SignatureStatusRequestFilter implements IgniteBiPredicate<String, Map<String, BinaryObject>> {
    private final long maxProcessingRetries;
    private final Duration processingTimeout;
    private final Duration exceptionTimeout;

    public SignatureStatusRequestFilter(long maxProcessingRetries, Duration processingTimeout, Duration exceptionTimeout) {
        this.maxProcessingRetries = maxProcessingRetries;
        this.processingTimeout = processingTimeout;
        this.exceptionTimeout = exceptionTimeout;
    }

    @Override
    public boolean apply(String containerSessionId, Map<String, BinaryObject> signatureSessions) {
        return signatureSessions.values().stream()
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

    static boolean isApplyFilter(SignatureStatusRequestFilter filter, ProcessingStatus processingStatus,
            LocalDateTime statusTimestamp,
            int processingCounter) {
        LocalDateTime currentTime = LocalDateTime.now();
        boolean isProcessingTimeout = ProcessingStatus.PROCESSING == processingStatus
                && currentTime.isAfter(statusTimestamp.plus(filter.processingTimeout));
        boolean isExceptionTimeout = ProcessingStatus.EXCEPTION == processingStatus
                && currentTime.isAfter(statusTimestamp.plus(filter.exceptionTimeout));
        return !isProcessingTimeout && !isExceptionTimeout && processingCounter <= filter.maxProcessingRetries;
    }
}
