package ee.openeid.siga.common.session;

import lombok.*;

import java.time.LocalDateTime;

import static ee.openeid.siga.common.session.ProcessingStatus.PROCESSING;
import static ee.openeid.siga.common.session.ProcessingStatus.RESULT;

@Data
@Builder
public class SessionStatus {
    private String status;
    private StatusError statusError;
    @Builder.Default
    private ProcessingStatus processingStatus = PROCESSING;
    private int processingCounter;
    @Setter(AccessLevel.PRIVATE)
    @Builder.Default
    private LocalDateTime processingStatusTimestamp = LocalDateTime.now();

    public void setProcessingStatus(ProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
        this.processingStatusTimestamp = LocalDateTime.now();
        if (processingStatus != RESULT) {
            processingCounter++;
        }
    }

    public void setStatusError(String errorCode, String message) {
        this.statusError = StatusError.builder()
                .errorCode(errorCode)
                .errorMessage(message)
                .build();
    }

    @Value
    @Builder
    public static class StatusError {
        String errorCode;
        String errorMessage;
    }
}
