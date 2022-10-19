package ee.openeid.siga.service.signature.container.status;

import ee.openeid.siga.common.session.SessionStatus;
import ee.openeid.siga.common.session.SessionStatus.ProcessingStatus;
import ee.openeid.siga.common.session.SignatureSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.internal.binary.BinaryEnumObjectImpl;
import org.apache.ignite.lang.IgniteBiPredicate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Predicate;

@Slf4j
public class SignatureStatusRequestFilter implements IgniteBiPredicate<String, Map<String, BinaryObject>> {
    private final long maxProcessingRetries;
    private final LocalDateTime processingTimeout;
    private final LocalDateTime exceptionTimeout;

    public SignatureStatusRequestFilter(long maxProcessingRetries, Duration processingTimeout, Duration exceptionTimeout) {
        this.maxProcessingRetries = maxProcessingRetries;
        this.processingTimeout = LocalDateTime.now().minusSeconds(processingTimeout.toSeconds());
        this.exceptionTimeout = LocalDateTime.now().minusSeconds(exceptionTimeout.toSeconds());
    }

    @Override
    public boolean apply(String containerSessionId, Map<String, BinaryObject> signatureSessions) {
        return signatureSessions.values().stream()
                .map(s -> (BinaryObject) s.field("sessionStatus"))
                .anyMatch(sessionStatus -> {
                    int statusOrdinal = sessionStatus.<BinaryEnumObjectImpl>field("processingStatus").enumOrdinal();
                    ProcessingStatus processingStatus = SessionStatus.ProcessingStatus.values()[statusOrdinal];
                    LocalDateTime statusTimestamp = sessionStatus.field("processingStatusTimestamp");
                    int processingCounter = sessionStatus.field("processingCounter");

                    log.trace("Container session: {}, Status: {}, Status timestamp: {}, Processing counter: {}", containerSessionId, processingStatus, statusTimestamp, processingCounter);
                    return isApplyFilter(processingStatus, statusTimestamp, processingCounter);
                });
    }

    public Predicate<Map.Entry<String, SignatureSession>> apply() {
        return s -> {
            SignatureSession signatureSession = s.getValue();
            SessionStatus sessionStatus = signatureSession.getSessionStatus();
            return isApplyFilter(sessionStatus.getProcessingStatus(), sessionStatus.getProcessingStatusTimestamp(), sessionStatus.getProcessingCounter());
        };
    }

    private boolean isApplyFilter(SessionStatus.ProcessingStatus processingStatus, LocalDateTime statusTimestamp, int processingCounter) {
        boolean isProcessingTimeout = ProcessingStatus.PROCESSING == processingStatus && statusTimestamp.isBefore(processingTimeout);
        boolean isExceptionTimeout = ProcessingStatus.EXCEPTION == processingStatus && statusTimestamp.isBefore(exceptionTimeout);
        return (isProcessingTimeout || isExceptionTimeout) && processingCounter <= maxProcessingRetries;
    }
}
