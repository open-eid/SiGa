package ee.openeid.siga.session.spi;

import java.time.Duration;
import java.time.Instant;

/**
 * Filter criteria for {@link SessionStatusScanner}. A session is a candidate for reprocessing
 * when its {@code processingStatus} is {@code PROCESSING} and its {@code processingStatusTimestamp}
 * is older than {@code processingCutoff}, OR its status is {@code EXCEPTION} and its timestamp is
 * older than {@code exceptionCutoff} — and in both cases its {@code processingCounter} has not
 * exceeded {@code maxProcessingRetries}.
 *
 * @param maxProcessingRetries upper bound on retry attempts per session
 * @param processingCutoff     sessions in {@code PROCESSING} older than this are candidates
 * @param exceptionCutoff      sessions in {@code EXCEPTION} older than this are candidates
 */
public record StatusReprocessingFilter(
        long maxProcessingRetries,
        Instant processingCutoff,
        Instant exceptionCutoff
) {
    public static StatusReprocessingFilter from(long maxProcessingRetries, Duration processingTimeout, Duration exceptionTimeout) {
        Instant now = Instant.now();
        return new StatusReprocessingFilter(
                maxProcessingRetries,
                now.minusSeconds(processingTimeout.toSeconds()),
                now.minusSeconds(exceptionTimeout.toSeconds())
        );
    }
}
