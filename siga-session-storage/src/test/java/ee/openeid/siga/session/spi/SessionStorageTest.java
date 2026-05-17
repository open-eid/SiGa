package ee.openeid.siga.session.spi;

import ee.openeid.siga.common.session.CertificateSession;
import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.common.session.ProcessingStatus;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.session.SessionStatus;
import ee.openeid.siga.common.session.SignatureSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Backend-agnostic contract tests for {@link SessionStorage}. Concrete subclasses wire a real
 * backend (Redis Testcontainer, embedded Ignite, …) and provide a fresh {@code storage()} per
 * test plus a {@code resetStorage()} hook used by {@link #cleanState()} to start each test from
 * an empty cache. Backend-specific edge cases (TTL refresh, cluster routing) live next to the
 * concrete subclass, not here.
 */
public abstract class SessionStorageTest {

    protected abstract SessionStorage storage();

    protected abstract void resetStorage();

    @BeforeEach
    void cleanState() {
        resetStorage();
    }

    @Test
    void shouldReturnEmpty_WhenGetCalledForMissingContainer() {
        assertTrue(storage().get("v1_svc_unknown").isEmpty());
    }

    @Test
    void shouldReturnEmpty_WhenPeekCalledForMissingContainer() {
        assertTrue(storage().peek("v1_svc_unknown_peek").isEmpty());
    }

    @Test
    void shouldReturnSession_WhenGetCalledAfterUpdate() {
        Session session = newSession("v1_svc_abc");
        storage().update(session);

        Optional<Session> loaded = storage().get(session.getSessionId());
        assertTrue(loaded.isPresent());
        assertEquals(session.getSessionId(), loaded.get().getSessionId());
    }

    @Test
    void shouldReturnSession_WhenPeekCalledForPresentContainer() {
        Session session = newSession("v1_svc_peek_present");
        storage().update(session);

        Optional<Session> loaded = storage().peek(session.getSessionId());
        assertTrue(loaded.isPresent());
        assertEquals(session.getSessionId(), loaded.get().getSessionId());
    }

    @Test
    void shouldReturnEmpty_WhenContainerRemoved() {
        Session session = newSession("v1_svc_remove");
        storage().update(session);
        storage().remove(session.getSessionId());

        assertTrue(storage().get(session.getSessionId()).isEmpty());
    }

    @Test
    void shouldNotThrow_WhenRemoveCalledForMissingContainer() {
        assertDoesNotThrow(() -> storage().remove("v1_svc_never_existed"));
    }

    @Test
    void shouldRoundTripSignatureAndCertificateSessions_WhenUpdateCalledThenGet() {
        Session session = newSessionWithSignatureAndCertificate("v1_svc_round_trip", "sig-1", "cert-1");
        storage().update(session);

        Optional<Session> loaded = storage().get(session.getSessionId());
        assertTrue(loaded.isPresent());
        Map<String, SignatureSession> signatures = loaded.get().getSignatureSessions();
        assertNotNull(signatures);
        assertEquals(1, signatures.size());
        assertEquals("sig-code-sig-1", signatures.get("sig-1").getSessionCode());
        Map<String, CertificateSession> certificates = loaded.get().getCertificateSessions();
        assertNotNull(certificates);
        assertEquals(1, certificates.size());
        assertEquals("cert-code-cert-1", certificates.get("cert-1").getSessionCode());

        Optional<Session> peeked = storage().peek(session.getSessionId());
        assertTrue(peeked.isPresent());
        assertEquals(1, peeked.get().getSignatureSessions().size());
        assertEquals(1, peeked.get().getCertificateSessions().size());
    }

    @Test
    void shouldReplaceSignatureAndCertificateSessions_WhenUpdateCalledAgain() {
        String sessionId = "v1_svc_replace_maps";
        storage().update(newSessionWithSignatureAndCertificate(sessionId, "sig-A", "cert-A"));
        storage().update(newSessionWithSignatureAndCertificate(sessionId, "sig-B", "cert-B"));

        Optional<Session> loaded = storage().get(sessionId);
        assertTrue(loaded.isPresent());
        Map<String, SignatureSession> signatures = loaded.get().getSignatureSessions();
        assertEquals(1, signatures.size());
        assertTrue(signatures.containsKey("sig-B"));
        assertFalse(signatures.containsKey("sig-A"));
        Map<String, CertificateSession> certificates = loaded.get().getCertificateSessions();
        assertEquals(1, certificates.size());
        assertTrue(certificates.containsKey("cert-B"));
        assertFalse(certificates.containsKey("cert-A"));
    }

    @Test
    void shouldOverwriteContainer_WhenUpdateCalledAgain() {
        String sessionId = "v1_svc_overwrite";
        storage().update(HashcodeContainerSession.builder()
                .sessionId(sessionId)
                .clientName("client-original")
                .serviceName("svc")
                .serviceUuid("uuid")
                .dataFiles(Collections.emptyList())
                .signatures(Collections.emptyList())
                .build());
        storage().update(HashcodeContainerSession.builder()
                .sessionId(sessionId)
                .clientName("client-updated")
                .serviceName("svc")
                .serviceUuid("uuid")
                .dataFiles(Collections.emptyList())
                .signatures(Collections.emptyList())
                .build());

        Optional<Session> loaded = storage().get(sessionId);
        assertTrue(loaded.isPresent());
        assertEquals("client-updated", loaded.get().getClientName());
    }

    @Test
    void shouldReportCount_WhenSizeCalled() {
        long initial = storage().size();
        storage().update(newSession("v1_svc_one"));
        storage().update(newSession("v1_svc_two"));
        assertEquals(initial + 2L, storage().size());
    }

    protected static Session newSession(String sessionId) {
        return HashcodeContainerSession.builder()
                .sessionId(sessionId)
                .clientName("client")
                .serviceName("svc")
                .serviceUuid("uuid")
                .dataFiles(Collections.emptyList())
                .signatures(Collections.emptyList())
                .build();
    }

    protected static Session newSessionWithSignatureAndCertificate(
            String sessionId, String signatureId, String certificateId) {
        Session session = newSession(sessionId);
        Map<String, SignatureSession> signatures = new HashMap<>();
        signatures.put(signatureId, SignatureSession.builder()
                .sessionCode("sig-code-" + signatureId)
                .sessionStatus(SessionStatus.builder()
                        .processingStatus(ProcessingStatus.PROCESSING)
                        .processingStatusTimestamp(Instant.now())
                        .build())
                .build());
        Map<String, CertificateSession> certificates = new HashMap<>();
        certificates.put(certificateId, CertificateSession.builder()
                .sessionCode("cert-code-" + certificateId)
                .documentNumber("doc-" + certificateId)
                .sessionStatus(SessionStatus.builder()
                        .processingStatus(ProcessingStatus.PROCESSING)
                        .processingStatusTimestamp(Instant.now())
                        .build())
                .build());
        session.setSignatureSessions(signatures);
        session.setCertificateSessions(certificates);
        return session;
    }
}
