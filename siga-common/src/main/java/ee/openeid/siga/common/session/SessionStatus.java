package ee.openeid.siga.common.session;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Setter;
import lombok.Value;

import java.io.Serializable;
import java.time.Instant;

import static ee.openeid.siga.common.session.ProcessingStatus.EXCEPTION;
import static ee.openeid.siga.common.session.ProcessingStatus.PROCESSING;
import static ee.openeid.siga.common.session.ProcessingStatus.RESULT;

@Data
@Builder
public class SessionStatus implements Serializable {
    private static final long serialVersionUID = 1L;

    private String status;
    private StatusError statusError;
    @Builder.Default
    private ProcessingStatus processingStatus = PROCESSING;
    private int processingCounter;
    @Setter(AccessLevel.PRIVATE)
    @Builder.Default
    private Instant processingStatusTimestamp = Instant.now();

    public void setProcessingStatus(ProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
        this.processingStatusTimestamp = Instant.now();
        if (processingStatus != RESULT) {
            processingCounter++;
        }
    }

    /**
     * True when this status is non-terminal ({@code PROCESSING} or {@code EXCEPTION}) and the
     * retry budget is not yet exhausted. Use this to decide whether the status reprocessor will
     * ever look at this entry again; pair with {@link #isDueForReprocessing} when the cutoff
     * also matters. The timestamp null-check guards against partial state from deserialization.
     */
    public boolean isPendingReprocessing(long maxProcessingAttempts) {
        return processingStatusTimestamp != null
                && processingCounter <= maxProcessingAttempts
                && (processingStatus == PROCESSING || processingStatus == EXCEPTION);
    }

    /**
     * True when this status is pending reprocessing and its {@code processingStatusTimestamp} is
     * older than the per-status cutoff — {@code PROCESSING} entries against {@code processingCutoff},
     * {@code EXCEPTION} entries against {@code exceptionCutoff}.
     */
    public boolean isDueForReprocessing(Instant processingCutoff,
                                        Instant exceptionCutoff,
                                        long maxProcessingAttempts) {
        if (!isPendingReprocessing(maxProcessingAttempts)) {
            return false;
        }
        Instant cutoff = processingStatus == PROCESSING ? processingCutoff : exceptionCutoff;
        return processingStatusTimestamp.isBefore(cutoff);
    }

    public void setStatusError(String errorCode, String message) {
        this.statusError = StatusError.builder()
                .errorCode(errorCode)
                .errorMessage(message)
                .build();
    }

    @Value
    @Builder
    public static class StatusError implements Serializable {
        private static final long serialVersionUID = 1L;

        String errorCode;
        String errorMessage;
    }
}
