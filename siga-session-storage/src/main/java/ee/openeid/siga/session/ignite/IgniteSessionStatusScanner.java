package ee.openeid.siga.session.ignite;

import ee.openeid.siga.session.spi.SessionStatusScanner;
import ee.openeid.siga.session.spi.StatusReprocessingFilter;
import lombok.RequiredArgsConstructor;
import org.apache.ignite.Ignite;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.ScanQuery;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Ignite implementation of the {@link SessionStatusScanner} SPI. Consumed by the status reprocessor
 * in {@code siga-webapp} to find container sessions whose signature/certificate sessions are stuck
 * in {@code PROCESSING} or {@code EXCEPTION} state past their cutoff and need to be re-polled.
 *
 * <p>Each scan issues an Ignite {@link ScanQuery} against {@code SIGNATURE_SESSION} or
 * {@code CERTIFICATE_SESSION} with a server-side {@link org.apache.ignite.lang.IgniteBiPredicate}
 * ({@link SignatureStatusRequestFilter} or {@link CertificateStatusRequestFilter}) and the shared
 * {@link SessionIdQueryTransformer}. All three are sent to server nodes via Ignite peer class
 * loading, so the query returns only the matching container {@code sessionId} strings instead of
 * shipping entire signature/certificate session maps to the client. {@code withKeepBinary()} keeps
 * the values as {@link org.apache.ignite.binary.BinaryObject}s — for {@code SIGNATURE_SESSION} the
 * filter relies on {@code SignatureSession} implementing {@link org.apache.ignite.binary.Binarylizable}
 * so the predicate can address fields directly on the binary form.
 *
 * <p>Results stream through {@link org.apache.ignite.cache.query.QueryCursor} to keep memory
 * bounded; the consumer is invoked synchronously on the calling thread.
 */
@RequiredArgsConstructor
public class IgniteSessionStatusScanner implements SessionStatusScanner {
    private final Ignite ignite;

    @Override
    public void scanSignatureSessions(StatusReprocessingFilter filter, Consumer<String> sessionIdConsumer) {
        SignatureStatusRequestFilter igniteFilter = new SignatureStatusRequestFilter(
                filter.maxProcessingRetries(),
                durationFromCutoff(filter.processingCutoff()),
                durationFromCutoff(filter.exceptionCutoff()));
        ScanQuery<String, Map<String, BinaryObject>> query = new ScanQuery<>(igniteFilter);
        try (QueryCursor<String> cursor = ignite.getOrCreateCache(CacheName.SIGNATURE_SESSION.name())
                .withKeepBinary()
                .query(query, new SessionIdQueryTransformer())) {
            cursor.forEach(sessionIdConsumer);
        }
    }

    @Override
    public void scanCertificateSessions(StatusReprocessingFilter filter, Consumer<String> sessionIdConsumer) {
        CertificateStatusRequestFilter igniteFilter = new CertificateStatusRequestFilter(
                filter.maxProcessingRetries(),
                durationFromCutoff(filter.processingCutoff()),
                durationFromCutoff(filter.exceptionCutoff()));
        ScanQuery<String, Map<String, BinaryObject>> query = new ScanQuery<>(igniteFilter);
        try (QueryCursor<String> cursor = ignite.getOrCreateCache(CacheName.CERTIFICATE_SESSION.name())
                .withKeepBinary()
                .query(query, new SessionIdQueryTransformer())) {
            cursor.forEach(sessionIdConsumer);
        }
    }

    private static Duration durationFromCutoff(LocalDateTime cutoff) {
        return Duration.between(cutoff, LocalDateTime.now());
    }
}
