package ee.openeid.siga.session.spi;

import java.util.function.Consumer;

/**
 * Scans the session storage for signature/certificate sessions that are candidates for status
 * reprocessing — i.e. sessions whose {@code processingStatus} is {@code PROCESSING} or
 * {@code EXCEPTION} and whose {@code processingStatusTimestamp} is older than the filter's cutoff.
 *
 * <p>The consumer is invoked at most once per matching <b>container</b> {@code sessionId} during a
 * single scan call. Implementations may cap the number of candidates returned per invocation to
 * bound scheduler work under large backlogs, so callers that need to drain all currently matching
 * sessions must repeat scans until no candidates are emitted. The consumer must not be {@code null}.
 * Implementations are expected to stream results (no unbounded materialization) and to complete
 * synchronously on the calling thread.
 *
 * <p>Implementations MAY mutate their own internal index state during a scan — for example to
 * remove stale entries pointing to expired sessions, re-align scores that have drifted since the
 * last update event, or evict members whose backing session no longer qualifies. Such maintenance
 * is part of the scan contract and is intentional: the canonical {@link SessionStorage} entry
 * remains the source of truth, and the scanner reconciles its own derived index against it
 * during the call. {@link SessionStorage} itself MUST NOT be mutated (no writes, no TTL refresh —
 * use {@link SessionStorage#peek} to read).
 *
 * <p>The caller re-checks {@code processingCounter &lt;= maxProcessingRetries} after loading each
 * session. Backends may filter exhausted entries earlier when their storage model supports it.
 */
public interface SessionStatusScanner {

    void scanSignatureSessions(StatusReprocessingFilter filter, Consumer<String> sessionIdConsumer);

    void scanCertificateSessions(StatusReprocessingFilter filter, Consumer<String> sessionIdConsumer);
}
