package ee.openeid.siga.session.redis;

import ee.openeid.siga.common.model.SigningType;
import ee.openeid.siga.common.session.CertificateSession;
import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.common.session.ProcessingStatus;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.session.SessionStatus;
import ee.openeid.siga.common.session.SignatureSession;
import ee.openeid.siga.session.configuration.SessionStatusReprocessingProperties;
import ee.openeid.siga.session.spi.ContainerExpiredEvent;
import ee.openeid.siga.session.spi.SessionRemovedEvent;
import ee.openeid.siga.session.spi.SessionUpdatedEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Async;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ee.openeid.siga.common.model.SigningType.REMOTE;
import static ee.openeid.siga.common.model.SigningType.SMART_ID;
import static ee.openeid.siga.common.session.ProcessingStatus.EXCEPTION;
import static ee.openeid.siga.common.session.ProcessingStatus.PROCESSING;
import static ee.openeid.siga.common.session.ProcessingStatus.RESULT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("docker")
@Testcontainers
class RedisSessionEventListenerTest {

    private static final int MAX_PROCESSING_ATTEMPTS = 10;

    @Container
    private static final GenericContainer<?> REDIS = RedisTestSupport.newRedisContainer();

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate stringTemplate;

    private RedisSessionEventListener listener;
    private SessionStatusReprocessingProperties properties;

    @BeforeAll
    static void initClients() {
        factory = RedisTestSupport.connectionFactory(REDIS);
        stringTemplate = RedisTestSupport.stringTemplate(factory);
    }

    @AfterAll
    static void closeClients() {
        if (factory != null) factory.destroy();
    }

    @BeforeEach
    void resetState() {
        RedisTestSupport.flushAll(factory);
        properties = retryProperties(MAX_PROCESSING_ATTEMPTS);
        listener = new RedisSessionEventListener(stringTemplate, properties);
    }

    @Test
    void shouldAddSignatureDueMember_WhenSignatureIsProcessing() {
        Instant timestamp = Instant.parse("2026-05-18T10:00:00Z");
        Session session = buildSession("v1_svc_sig_due",
                Map.of("sig-1", signatureSession(PROCESSING, timestamp)),
                Collections.emptyMap());

        listener.onSessionUpdated(new SessionUpdatedEvent(session));

        assertScore(RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, session.getSessionId(),
                timestamp.plus(properties.processingTimeout()));
    }

    @Test
    void shouldAddCertificateDueMember_WhenCertificateIsException() {
        Instant timestamp = Instant.parse("2026-05-18T10:00:00Z");
        Session session = buildSession("v1_svc_cert_due",
                Collections.emptyMap(),
                Map.of("cert-1", certificateSession(EXCEPTION, timestamp)));

        listener.onSessionUpdated(new SessionUpdatedEvent(session));

        assertScore(RedisSessionKeys.CERTIFICATE_REPROCESSING_QUEUE, session.getSessionId(),
                timestamp.plus(properties.exceptionTimeout()));
    }

    @Test
    void shouldUseMinimumDueScore_WhenMultipleSignatureSessionsQualify() {
        Instant earlierDue = Instant.parse("2026-05-18T10:00:00Z");
        Instant laterDue = Instant.parse("2026-05-18T10:01:00Z");
        Session session = buildSession("v1_svc_min_due",
                Map.of(
                        "sig-1", signatureSession(PROCESSING, earlierDue),
                        "sig-2", signatureSession(EXCEPTION, laterDue)),
                Collections.emptyMap());

        listener.onSessionUpdated(new SessionUpdatedEvent(session));

        assertScore(RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, session.getSessionId(),
                earlierDue.plus(properties.processingTimeout()));
    }

    @Test
    void shouldRemoveDueMember_WhenTerminalStatusReplacesQualifyingWork() {
        Session session = sessionWithSignature("v1_svc_done", PROCESSING, Instant.now().minus(Duration.ofMinutes(10)));
        listener.onSessionUpdated(new SessionUpdatedEvent(session));
        assertNotNull(score(RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, session.getSessionId()));

        session.getSignatureSessions().get("sig-1").getSessionStatus().setProcessingStatus(RESULT);
        listener.onSessionUpdated(new SessionUpdatedEvent(session));

        assertNull(score(RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, session.getSessionId()));
    }

    @Test
    void shouldRecomputeScore_WhenSignatureTransitionsFromProcessingToException() {
        // PROCESSING and EXCEPTION both qualify for the queue but use different timeouts
        // (processingTimeout vs exceptionTimeout). The score must be recomputed on transition so
        // a signature stuck in EXCEPTION isn't held back by the longer PROCESSING-bucket timeout.
        // Updating the processing status also resets the status timestamp, so the expected
        // exception score uses the timestamp read after the transition.
        Instant processingTimestamp = Instant.parse("2026-05-18T10:00:00Z");
        Session session = sessionWithSignature("v1_svc_transition", PROCESSING, processingTimestamp);
        listener.onSessionUpdated(new SessionUpdatedEvent(session));
        Double processingScore = score(RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, session.getSessionId());

        SessionStatus status = session.getSignatureSessions().get("sig-1").getSessionStatus();
        status.setProcessingStatus(EXCEPTION);
        Instant exceptionTimestamp = status.getProcessingStatusTimestamp();
        listener.onSessionUpdated(new SessionUpdatedEvent(session));
        Double exceptionScore = score(RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, session.getSessionId());

        assertNotNull(processingScore);
        assertNotNull(exceptionScore);
        assertEquals(processingTimestamp.plus(properties.processingTimeout()).toEpochMilli(),
                processingScore.longValue());
        assertEquals(exceptionTimestamp.plus(properties.exceptionTimeout()).toEpochMilli(),
                exceptionScore.longValue());
    }

    @Test
    void shouldNotAddDueMember_WhenRetryAttemptsAreExhausted() {
        Session session = buildSession("v1_svc_exhausted",
                Map.of("sig-1", signatureSession(PROCESSING, Instant.now().minus(Duration.ofMinutes(10)),
                        MAX_PROCESSING_ATTEMPTS + 1)),
                Collections.emptyMap());

        listener.onSessionUpdated(new SessionUpdatedEvent(session));

        assertNull(score(RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, session.getSessionId()));
    }

    @Test
    void shouldNotAddDueMember_WhenSignatureIsRemote() {
        Session session = buildSession("v1_svc_remote",
                Map.of("sig-1", signatureSession(PROCESSING, Instant.now().minus(Duration.ofMinutes(10)), 0, REMOTE)),
                Collections.emptyMap());

        listener.onSessionUpdated(new SessionUpdatedEvent(session));

        assertNull(score(RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, session.getSessionId()));
    }

    @Test
    void shouldIgnoreEntriesWithNullStatusOrTimestamp() {
        Map<String, SignatureSession> signatures = new HashMap<>();
        signatures.put("sig-no-status", SignatureSession.builder()
                .signingType(SMART_ID)
                .sessionStatus(null)
                .build());
        signatures.put("sig-no-timestamp", SignatureSession.builder()
                .signingType(SMART_ID)
                .sessionStatus(SessionStatus.builder()
                        .processingStatus(PROCESSING)
                        .processingStatusTimestamp(null)
                        .build())
                .build());
        Session session = buildSession("v1_svc_null_status", signatures, Collections.emptyMap());

        listener.onSessionUpdated(new SessionUpdatedEvent(session));

        assertNull(score(RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, session.getSessionId()));
    }

    @Test
    void shouldRemoveSessionFromBothQueues_WhenSessionRemoved() {
        Session session = buildSession("v1_svc_removed",
                Map.of("sig-1", signatureSession(PROCESSING, Instant.now().minus(Duration.ofMinutes(10)))),
                Map.of("cert-1", certificateSession(EXCEPTION, Instant.now().minus(Duration.ofMinutes(10)))));
        listener.onSessionUpdated(new SessionUpdatedEvent(session));
        assertNotNull(score(RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, session.getSessionId()));
        assertNotNull(score(RedisSessionKeys.CERTIFICATE_REPROCESSING_QUEUE, session.getSessionId()));

        listener.onSessionRemoved(new SessionRemovedEvent(session.getSessionId()));

        assertNull(score(RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, session.getSessionId()));
        assertNull(score(RedisSessionKeys.CERTIFICATE_REPROCESSING_QUEUE, session.getSessionId()));
    }

    @Test
    void shouldRemoveSessionFromBothQueues_WhenContainerExpired() {
        Session session = buildSession("v1_svc_expired",
                Map.of("sig-1", signatureSession(PROCESSING, Instant.now().minus(Duration.ofMinutes(10)))),
                Map.of("cert-1", certificateSession(EXCEPTION, Instant.now().minus(Duration.ofMinutes(10)))));
        listener.onSessionUpdated(new SessionUpdatedEvent(session));

        listener.onContainerExpired(new ContainerExpiredEvent(session.getSessionId()));

        assertNull(score(RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, session.getSessionId()));
        assertNull(score(RedisSessionKeys.CERTIFICATE_REPROCESSING_QUEUE, session.getSessionId()));
    }

    @Test
    void shouldExecuteBothQueueUpdatesAtomically_WhenSessionUpdated() {
        // A session update must submit one script invocation with both due-queue keys passed as
        // Redis KEYS. That keeps cluster routing and the KEYS/ARGS boundary explicit.
        StringRedisTemplate spyTemplate = Mockito.spy(stringTemplate);
        RedisSessionEventListener spyListener = new RedisSessionEventListener(spyTemplate, properties);
        String sessionId = "v1_svc_pipelined";

        spyListener.onSessionUpdated(new SessionUpdatedEvent(buildSession(sessionId,
                Map.of("sig-1", signatureSession(PROCESSING, Instant.now().minus(Duration.ofMinutes(10)))),
                Map.of("cert-1", certificateSession(EXCEPTION, Instant.now().minus(Duration.ofMinutes(10)))))));

        Mockito.verify(spyTemplate, Mockito.times(1))
                .execute(Mockito.any(RedisScript.class),
                        Mockito.eq(List.of(
                                RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE,
                                RedisSessionKeys.CERTIFICATE_REPROCESSING_QUEUE)),
                        Mockito.eq(sessionId),
                        Mockito.anyString(),
                        Mockito.anyString());
    }

    @Test
    void shouldOffloadContainerExpired_ButNotSessionUpdatedOrRemoved() {
        Method onContainerExpired = methodOf("onContainerExpired", ContainerExpiredEvent.class);
        Method onSessionUpdated = methodOf("onSessionUpdated", SessionUpdatedEvent.class);
        Method onSessionRemoved = methodOf("onSessionRemoved", SessionRemovedEvent.class);

        assertTrue(onContainerExpired.isAnnotationPresent(Async.class));
        assertFalse(onSessionUpdated.isAnnotationPresent(Async.class));
        assertFalse(onSessionRemoved.isAnnotationPresent(Async.class));
    }

    @Test
    void shouldNotThrow_WhenContainerExpiredEventCarriesNullSessionId() {
        assertDoesNotThrow(() -> listener.onContainerExpired(new ContainerExpiredEvent(null)));
    }

    private Double score(String queueKey, String sessionId) {
        return stringTemplate.opsForZSet().score(queueKey, sessionId);
    }

    private void assertScore(String queueKey, String sessionId, Instant expectedDueAt) {
        Double score = score(queueKey, sessionId);
        assertNotNull(score);
        assertEquals(expectedDueAt.toEpochMilli(), score.longValue());
    }

    private static Method methodOf(String name, Class<?>... params) {
        try {
            return RedisSessionEventListener.class.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    private static SessionStatusReprocessingProperties retryProperties(int maxProcessingAttempts) {
        return new SessionStatusReprocessingProperties(
                maxProcessingAttempts, Duration.ofSeconds(30), Duration.ofSeconds(5));
    }

    private static Session sessionWithSignature(String sessionId, ProcessingStatus status, Instant timestamp) {
        return buildSession(sessionId, Map.of("sig-1", signatureSession(status, timestamp)), Collections.emptyMap());
    }

    private static Session buildSession(String sessionId,
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

    private static SignatureSession signatureSession(ProcessingStatus status, Instant timestamp) {
        return signatureSession(status, timestamp, 0);
    }

    private static SignatureSession signatureSession(ProcessingStatus status, Instant timestamp, int processingCounter) {
        return signatureSession(status, timestamp, processingCounter, SMART_ID);
    }

    private static SignatureSession signatureSession(ProcessingStatus status, Instant timestamp,
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

    private static CertificateSession certificateSession(ProcessingStatus status, Instant timestamp) {
        return CertificateSession.builder()
                .sessionStatus(SessionStatus.builder()
                        .processingStatus(status)
                        .processingStatusTimestamp(timestamp)
                        .build())
                .build();
    }

}
