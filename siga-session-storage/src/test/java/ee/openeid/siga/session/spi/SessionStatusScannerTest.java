package ee.openeid.siga.session.spi;

import ee.openeid.siga.common.model.SigningType;
import ee.openeid.siga.common.session.CertificateSession;
import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.common.session.ProcessingStatus;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.session.SessionStatus;
import ee.openeid.siga.common.session.SignatureSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static ee.openeid.siga.common.model.SigningType.SMART_ID;
import static ee.openeid.siga.common.session.ProcessingStatus.EXCEPTION;
import static ee.openeid.siga.common.session.ProcessingStatus.PROCESSING;
import static ee.openeid.siga.common.session.ProcessingStatus.RESULT;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Backend-agnostic contract tests for {@link SessionStatusScanner}. Concrete subclasses wire a
 * real backend (Redis Testcontainer, embedded Ignite, …), provide a fresh {@code scanner()} per
 * test, a {@code seedSession(Session)} hook that primes whatever storage/index state the backend's
 * scanner reads from, and a {@code resetState()} hook used by {@link #cleanState()} to start each
 * test from an empty cache.
 *
 * <p>Per the SPI doc, the {@code processingCounter <= maxProcessingRetries} check is the caller's
 * responsibility — backends MAY enforce it server-side. This base only covers the cutoff/status
 * semantics; backend-specific retry-counter behaviour lives in the concrete subclass.
 */
public abstract class SessionStatusScannerTest {

    private static final Instant AGED = Instant.now().minus(Duration.ofMinutes(10));
    private static final Instant FRESH = Instant.now().plus(Duration.ofMinutes(10));

    protected abstract SessionStatusScanner scanner();

    protected abstract void seedSession(Session session);

    protected abstract void resetState();

    @BeforeEach
    void cleanState() {
        resetState();
    }

    @Test
    void shouldNotInvokeConsumer_WhenScanningSignaturesOnEmptyStorage() {
        AtomicInteger calls = new AtomicInteger();
        scanner().scanSignatureSessions(filterWithCutoffNow(), id -> calls.incrementAndGet());
        assertEquals(0, calls.get());
    }

    @Test
    void shouldNotInvokeConsumer_WhenScanningCertificatesOnEmptyStorage() {
        AtomicInteger calls = new AtomicInteger();
        scanner().scanCertificateSessions(filterWithCutoffNow(), id -> calls.incrementAndGet());
        assertEquals(0, calls.get());
    }

    @Test
    void shouldEmitSessionId_WhenSignatureSessionInProcessingPastCutoff() {
        seedSession(sessionWithSignature("v1_svc_sig_proc", PROCESSING, AGED));

        List<String> emitted = collectSignatureScan();

        assertEquals(List.of("v1_svc_sig_proc"), emitted);
    }

    @Test
    void shouldEmitSessionId_WhenSignatureSessionInExceptionPastCutoff() {
        seedSession(sessionWithSignature("v1_svc_sig_exc", EXCEPTION, AGED));

        List<String> emitted = collectSignatureScan();

        assertEquals(List.of("v1_svc_sig_exc"), emitted);
    }

    @Test
    void shouldEmitSessionId_WhenCertificateSessionInProcessingPastCutoff() {
        seedSession(sessionWithCertificate("v1_svc_cert_proc", PROCESSING, AGED));

        List<String> emitted = collectCertificateScan();

        assertEquals(List.of("v1_svc_cert_proc"), emitted);
    }

    @Test
    void shouldEmitSessionId_WhenCertificateSessionInExceptionPastCutoff() {
        seedSession(sessionWithCertificate("v1_svc_cert_exc", EXCEPTION, AGED));

        List<String> emitted = collectCertificateScan();

        assertEquals(List.of("v1_svc_cert_exc"), emitted);
    }

    @Test
    void shouldNotEmit_WhenProcessingTimestampIsAfterCutoff() {
        seedSession(sessionWithSignature("v1_svc_fresh_proc", PROCESSING, FRESH));
        seedSession(sessionWithCertificate("v1_svc_fresh_proc_cert", PROCESSING, FRESH));

        assertEquals(List.of(), collectSignatureScan());
        assertEquals(List.of(), collectCertificateScan());
    }

    @Test
    void shouldNotEmit_WhenStatusIsResult() {
        seedSession(sessionWithSignature("v1_svc_sig_done", RESULT, AGED));
        seedSession(sessionWithCertificate("v1_svc_cert_done", RESULT, AGED));

        assertEquals(List.of(), collectSignatureScan());
        assertEquals(List.of(), collectCertificateScan());
    }

    @Test
    void shouldNotEmitFromCertificateScan_WhenOnlySignatureQualifies() {
        seedSession(sessionWithSignature("v1_svc_sig_only", PROCESSING, AGED));

        assertEquals(List.of(), collectCertificateScan());
    }

    @Test
    void shouldNotEmitFromSignatureScan_WhenOnlyCertificateQualifies() {
        seedSession(sessionWithCertificate("v1_svc_cert_only", PROCESSING, AGED));

        assertEquals(List.of(), collectSignatureScan());
    }

    @Test
    void shouldEmitOncePerSession_WhenMultipleSignaturesQualify() {
        Map<String, SignatureSession> signatures = new HashMap<>();
        signatures.put("sig-1", signatureSession(PROCESSING, AGED));
        signatures.put("sig-2", signatureSession(PROCESSING, AGED.minus(Duration.ofMinutes(5))));
        seedSession(buildSession("v1_svc_multi_sig", signatures, Collections.emptyMap()));

        List<String> emitted = collectSignatureScan();

        assertEquals(List.of("v1_svc_multi_sig"), emitted);
    }

    @Test
    void shouldEmitOncePerSession_WhenSignaturesQualifyInBothBuckets() {
        Map<String, SignatureSession> signatures = new HashMap<>();
        signatures.put("sig-proc", signatureSession(PROCESSING, AGED));
        signatures.put("sig-exc", signatureSession(EXCEPTION, AGED.minus(Duration.ofMinutes(5))));
        seedSession(buildSession("v1_svc_both_sig_buckets", signatures, Collections.emptyMap()));

        List<String> emitted = collectSignatureScan();

        assertEquals(List.of("v1_svc_both_sig_buckets"), emitted);
    }

    @Test
    void shouldEmitOncePerSession_WhenCertificatesQualifyInBothBuckets() {
        Map<String, CertificateSession> certificates = new HashMap<>();
        certificates.put("cert-proc", certificateSession(PROCESSING, AGED));
        certificates.put("cert-exc", certificateSession(EXCEPTION, AGED.minus(Duration.ofMinutes(5))));
        seedSession(buildSession("v1_svc_both_cert_buckets", Collections.emptyMap(), certificates));

        List<String> emitted = collectCertificateScan();

        assertEquals(List.of("v1_svc_both_cert_buckets"), emitted);
    }

    @Test
    void shouldEmitMultipleSessionIds_WhenMultipleContainersQualify() {
        seedSession(sessionWithSignature("v1_svc_a", PROCESSING, AGED));
        seedSession(sessionWithSignature("v1_svc_b", PROCESSING, AGED));
        seedSession(sessionWithSignature("v1_svc_c", EXCEPTION, AGED));

        Set<String> emitted = Set.copyOf(collectSignatureScan());

        assertEquals(Set.of("v1_svc_a", "v1_svc_b", "v1_svc_c"), emitted);
    }

    private List<String> collectSignatureScan() {
        List<String> ids = new ArrayList<>();
        scanner().scanSignatureSessions(filterWithCutoffNow(), ids::add);
        return ids;
    }

    private List<String> collectCertificateScan() {
        List<String> ids = new ArrayList<>();
        scanner().scanCertificateSessions(filterWithCutoffNow(), ids::add);
        return ids;
    }

    /**
     * Cutoffs at "now" cleanly separate sessions seeded with {@link #AGED} (10 min in the past, so
     * past the cutoff and a candidate) from those seeded with {@link #FRESH} (10 min in the future,
     * so still within the window). The retry ceiling is set high to keep this contract-level test
     * orthogonal to backend-specific retry-counter handling.
     */
    protected static StatusReprocessingFilter filterWithCutoffNow() {
        Instant now = Instant.now();
        return new StatusReprocessingFilter(Long.MAX_VALUE, now, now);
    }

    protected static Session sessionWithSignature(String sessionId, ProcessingStatus status, Instant timestamp) {
        return buildSession(sessionId, Map.of("sig-1", signatureSession(status, timestamp)), Collections.emptyMap());
    }

    protected static Session sessionWithCertificate(String sessionId, ProcessingStatus status, Instant timestamp) {
        return buildSession(sessionId, Collections.emptyMap(), Map.of("cert-1", certificateSession(status, timestamp)));
    }

    protected static Session buildSession(String sessionId,
                                          Map<String, SignatureSession> signatures,
                                          Map<String, CertificateSession> certificates) {
        Session session = HashcodeContainerSession.builder()
                .sessionId(sessionId)
                .clientName("client")
                .serviceName("svc")
                .serviceUuid("uuid")
                .dataFiles(Collections.emptyList())
                .signatures(Collections.emptyList())
                .build();
        session.setSignatureSessions(new HashMap<>(signatures));
        session.setCertificateSessions(new HashMap<>(certificates));
        return session;
    }

    protected static SignatureSession signatureSession(ProcessingStatus status, Instant timestamp) {
        return signatureSession(status, timestamp, 0);
    }

    protected static SignatureSession signatureSession(ProcessingStatus status, Instant timestamp, int processingCounter) {
        return signatureSession(status, timestamp, processingCounter, SMART_ID);
    }

    protected static SignatureSession signatureSession(ProcessingStatus status, Instant timestamp,
                                                       int processingCounter, SigningType signingType) {
        return SignatureSession.builder()
                .signingType(signingType)
                .sessionStatus(SessionStatus.builder()
                        .processingStatus(status)
                        .processingStatusTimestamp(timestamp)
                        .processingCounter(processingCounter)
                        .build())
                .build();
    }

    protected static CertificateSession certificateSession(ProcessingStatus status, Instant timestamp) {
        return certificateSession(status, timestamp, 0);
    }

    protected static CertificateSession certificateSession(ProcessingStatus status, Instant timestamp, int processingCounter) {
        return CertificateSession.builder()
                .sessionStatus(SessionStatus.builder()
                        .processingStatus(status)
                        .processingStatusTimestamp(timestamp)
                        .processingCounter(processingCounter)
                        .build())
                .build();
    }

}
