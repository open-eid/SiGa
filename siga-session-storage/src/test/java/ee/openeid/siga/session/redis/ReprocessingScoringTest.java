package ee.openeid.siga.session.redis;

import ee.openeid.siga.common.model.SigningType;
import ee.openeid.siga.common.session.CertificateSession;
import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.common.session.ProcessingStatus;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.session.SessionStatus;
import ee.openeid.siga.common.session.SignatureSession;
import ee.openeid.siga.session.configuration.SessionStatusReprocessingProperties;
import ee.openeid.siga.session.redis.ReprocessingScoring.QueueType;
import ee.openeid.siga.session.spi.StatusReprocessingFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReprocessingScoringTest {

    private static final Instant AGED = Instant.parse("2026-05-18T10:00:00Z");

    @Test
    void shouldRejectAtFilter_WhenProcessingCounterExceedsFilterRetriesButPassesPropertyAttempts() {
        // properties.maxProcessingAttempts is the scoring gate (queue membership);
        // filter.maxProcessingRetries is the scan-time gate (emission). The two can diverge —
        // a session with counter=7 passes scoring against attempts=10 but is rejected by
        // emission against retries=5. Pin the divergence so a future refactor that conflates
        // the two breaks the test.
        // withDefaults() pins maxProcessingAttempts=10, the scoring gate this scenario depends on.
        SessionStatusReprocessingProperties properties = SessionStatusReprocessingProperties.withDefaults();
        Session session = sessionWithSignature(SigningType.SMART_ID, ProcessingStatus.PROCESSING, AGED, 7);

        Long score = ReprocessingScoring.scoreFor(session, QueueType.SIGNATURE, properties);
        StatusReprocessingFilter filter = new StatusReprocessingFilter(
                5L,
                AGED.plusSeconds(60),
                AGED.plusSeconds(60));
        boolean due = ReprocessingScoring.hasDueWork(session, QueueType.SIGNATURE, filter);

        assertNotNull(score, "scoring must accept counter <= properties.maxProcessingAttempts");
        assertFalse(due, "filter must reject counter > filter.maxProcessingRetries");
    }

    @Test
    void shouldIncludeMobileIdSignaturesInSignatureQueueScoring() {
        // QueueType.SIGNATURE filters by isPollable, which accepts SMART_ID and MOBILE_ID and
        // rejects REMOTE. SMART_ID and REMOTE have integration coverage; MOBILE_ID does not.
        // Without this test, narrowing isPollable to SMART_ID-only would not surface in CI.
        SessionStatusReprocessingProperties properties = SessionStatusReprocessingProperties.withDefaults();
        Session mobileIdSession = sessionWithSignature(
                SigningType.MOBILE_ID, ProcessingStatus.PROCESSING, AGED, 0);
        Session remoteSession = sessionWithSignature(
                SigningType.REMOTE, ProcessingStatus.PROCESSING, AGED, 0);

        Long mobileScore = ReprocessingScoring.scoreFor(mobileIdSession, QueueType.SIGNATURE, properties);
        Long remoteScore = ReprocessingScoring.scoreFor(remoteSession, QueueType.SIGNATURE, properties);

        long expected = AGED.plus(properties.processingTimeout()).toEpochMilli();
        assertNotNull(mobileScore, "MOBILE_ID signatures must be scored");
        assertEquals(expected, mobileScore.longValue());
        assertNull(remoteScore, "REMOTE signatures must be excluded from scoring");
    }

    @Test
    @ResourceLock(Resources.TIME_ZONE)
    void shouldIgnoreJvmDefaultZone_WhenScoring() {
        // Scores are shared across nodes via the Redis ZSET, so they must be pure epoch math.
        // A regression to a zone-dependent type (e.g. LocalDateTime interpreted in the default
        // zone) would skew scores between nodes running in different timezones and across DST
        // transitions; pin that the JVM default zone has no effect on the produced score.
        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            SessionStatusReprocessingProperties properties = SessionStatusReprocessingProperties.withDefaults();
            Session session = sessionWithSignature(SigningType.SMART_ID, ProcessingStatus.PROCESSING, AGED, 0);

            Long score = ReprocessingScoring.scoreFor(session, QueueType.SIGNATURE, properties);

            assertNotNull(score);
            assertEquals(AGED.plus(properties.processingTimeout()).toEpochMilli(), score.longValue());
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Test
    void shouldReturnNullScore_WhenSessionHasNullInnerMaps() {
        // RedisSessionStorage.readSession can produce a Session whose signatureSessions /
        // certificateSessions are null (the storage's Optional.ofNullable wrapping anticipates
        // this). QueueType.values(...) guards against null with an empty Stream; exercise that
        // guard explicitly rather than rely on the test helpers always wrapping in HashMap.
        SessionStatusReprocessingProperties properties = SessionStatusReprocessingProperties.withDefaults();
        Session session = HashcodeContainerSession.builder()
                .sessionId("v1_svc_null_maps")
                .clientName("client")
                .serviceName("svc")
                .serviceUuid("uuid")
                .dataFiles(Collections.emptyList())
                .signatures(Collections.emptyList())
                .build();
        session.setSignatureSessions(null);
        session.setCertificateSessions(null);

        Long signatureScore = ReprocessingScoring.scoreFor(session, QueueType.SIGNATURE, properties);
        Long certificateScore = ReprocessingScoring.scoreFor(session, QueueType.CERTIFICATE, properties);

        assertNull(signatureScore);
        assertNull(certificateScore);
    }

    private static Session sessionWithSignature(SigningType signingType,
                                                ProcessingStatus status,
                                                Instant timestamp,
                                                int processingCounter) {
        SignatureSession signature = SignatureSession.builder()
                .signingType(signingType)
                .sessionStatus(SessionStatus.builder()
                        .processingStatus(status)
                        .processingStatusTimestamp(timestamp)
                        .processingCounter(processingCounter)
                        .build())
                .build();
        Session session = HashcodeContainerSession.builder()
                .sessionId("v1_svc_test")
                .clientName("client")
                .serviceName("svc")
                .serviceUuid("uuid")
                .dataFiles(Collections.emptyList())
                .signatures(Collections.emptyList())
                .build();
        Map<String, SignatureSession> signatures = new HashMap<>();
        signatures.put("sig-1", signature);
        session.setSignatureSessions(signatures);
        session.setCertificateSessions(Collections.<String, CertificateSession>emptyMap());
        return session;
    }
}
