package ee.openeid.siga.session.redis;

import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.session.configuration.SessionStatusReprocessingProperties;
import ee.openeid.siga.session.spi.SessionStatusScanner;
import ee.openeid.siga.session.spi.SessionStatusScannerTest;
import ee.openeid.siga.session.spi.SessionStorage;
import ee.openeid.siga.session.spi.SessionUpdatedEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static ee.openeid.siga.common.session.ProcessingStatus.PROCESSING;
import static ee.openeid.siga.common.session.ProcessingStatus.RESULT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("docker")
@Testcontainers
class RedisSessionStatusScannerTest extends SessionStatusScannerTest {

    private static final int BATCH_SIZE = 5;
    private static final int MAX_PROCESSING_ATTEMPTS = 10;

    @Container
    private static final GenericContainer<?> REDIS = RedisTestSupport.newRedisContainer();

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate stringTemplate;
    private static RedisTemplate<String, Object> sessionTemplate;

    private RedisSessionStorage storage;
    private RedisSessionEventListener listener;
    private RedisSessionStatusScanner scanner;
    private SessionStatusReprocessingProperties properties;

    @BeforeAll
    static void initClients() {
        factory = RedisTestSupport.connectionFactory(REDIS);
        stringTemplate = RedisTestSupport.stringTemplate(factory);
        sessionTemplate = RedisTestSupport.sessionTemplate(factory);
    }

    @AfterAll
    static void closeClients() {
        if (factory != null) factory.destroy();
    }

    @Override
    protected SessionStatusScanner scanner() {
        return scanner;
    }

    @Override
    protected void seedSession(Session session) {
        storage.update(session);
        listener.onSessionUpdated(new SessionUpdatedEvent(session));
    }

    @Override
    protected void resetState() {
        RedisTestSupport.flushAll(factory);
        storage = new RedisSessionStorage(sessionTemplate, Duration.ofMinutes(5));
        properties = retryProperties(MAX_PROCESSING_ATTEMPTS);
        listener = new RedisSessionEventListener(stringTemplate, properties);
        scanner = new RedisSessionStatusScanner(stringTemplate, storage, properties, BATCH_SIZE);
    }

    @Test
    void shouldRespectBatchSize_WhenManySignatureSessionsAreDue() {
        scanner = new RedisSessionStatusScanner(stringTemplate, storage, properties, 1);
        for (int i = 0; i < 3; i++) {
            seedSession(sessionWithSignature("v1_svc_batch_" + i, PROCESSING,
                    Instant.now().minus(Duration.ofMinutes(10)).minusSeconds(i)));
        }

        List<String> emitted = new ArrayList<>();
        scanner.scanSignatureSessions(filterWithCutoffNow(), emitted::add);

        assertEquals(1, emitted.size());
    }

    @Test
    void shouldNotRefreshSessionTtl_WhenScannerPeeksCandidate() {
        Session session = sessionWithSignature("v1_svc_no_ttl_refresh", PROCESSING,
                Instant.now().minus(Duration.ofMinutes(10)));
        seedSession(session);
        String sessionKey = RedisSessionKeys.session(session.getSessionId());
        sessionTemplate.expire(sessionKey, Duration.ofSeconds(10));
        Long ttlBeforeScan = sessionTemplate.getExpire(sessionKey, TimeUnit.MILLISECONDS);

        scanner.scanSignatureSessions(filterWithCutoffNow(), id -> {
        });

        Long ttlAfterScan = sessionTemplate.getExpire(sessionKey, TimeUnit.MILLISECONDS);
        assertNotNull(ttlBeforeScan);
        assertNotNull(ttlAfterScan);
        assertTrue(ttlAfterScan <= ttlBeforeScan,
                "scanning a due entry must not refresh the Redis session TTL");
    }

    @Test
    void shouldRemoveDueMember_WhenSessionValueDoesNotExist() {
        String sessionId = "v1_svc_missing_payload";
        stringTemplate.opsForZSet().add(RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, sessionId, 1L);

        List<String> emitted = new ArrayList<>();
        scanner.scanSignatureSessions(filterWithCutoffNow(), emitted::add);

        assertTrue(emitted.isEmpty());
        assertNull(score(RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, sessionId));
    }

    @Test
    void shouldRemoveDueMember_WhenLiveSessionNoLongerHasMatchingWork() {
        String sessionId = "v1_svc_stale_done";
        storage.update(sessionWithSignature(sessionId, RESULT, Instant.now().minus(Duration.ofMinutes(10))));
        stringTemplate.opsForZSet().add(RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, sessionId, 1L);

        List<String> emitted = new ArrayList<>();
        scanner.scanSignatureSessions(filterWithCutoffNow(), emitted::add);

        assertTrue(emitted.isEmpty());
        assertNull(score(RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, sessionId));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSkipNullMember_WhenZRangeByScoreReturnsNullSessionId() {
        // Real Redis cannot emit a null ZSET member; this exercises the defensive branch via
        // a mocked ZSetOperations.
        StringRedisTemplate mockTemplate = Mockito.mock(StringRedisTemplate.class);
        ZSetOperations<String, String> mockZSet = Mockito.mock(ZSetOperations.class);
        SessionStorage mockStorage = Mockito.mock(SessionStorage.class);
        Mockito.when(mockTemplate.opsForZSet()).thenReturn(mockZSet);
        Set<String> withNull = new LinkedHashSet<>();
        withNull.add(null);
        Mockito.when(mockZSet.rangeByScore(
                Mockito.eq(RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE),
                Mockito.anyDouble(), Mockito.anyDouble(),
                Mockito.anyLong(), Mockito.anyLong())).thenReturn(withNull);

        RedisSessionStatusScanner mockedScanner = new RedisSessionStatusScanner(
                mockTemplate, mockStorage, properties, BATCH_SIZE);
        List<String> emitted = new ArrayList<>();

        assertDoesNotThrow(() -> mockedScanner.scanSignatureSessions(filterWithCutoffNow(), emitted::add));

        assertTrue(emitted.isEmpty());
        Mockito.verify(mockStorage, Mockito.never()).peek(Mockito.any());
        Mockito.verify(mockZSet, Mockito.never())
                .remove(Mockito.anyString(), Mockito.<Object[]>any());
    }

    @Test
    void shouldReAlignScore_WhenSessionHasFreshTimestampButQueueHasStaleScore() {
        // A stale due-queue score can lag behind the session's current retry timestamp. The scan
        // must re-align the score rather than emit the candidate prematurely.
        Instant freshTimestamp = Instant.now();
        Session session = sessionWithSignature("v1_svc_drift", PROCESSING, freshTimestamp);
        storage.update(session);
        long staleScore = Instant.now().minus(Duration.ofMinutes(10)).toEpochMilli();
        stringTemplate.opsForZSet().add(
                RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, session.getSessionId(), staleScore);

        List<String> emitted = new ArrayList<>();
        scanner.scanSignatureSessions(filterWithCutoffNow(), emitted::add);

        assertTrue(emitted.isEmpty(),
                "session with future retry score must not be emitted before its due time");
        Double newScore = score(RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, session.getSessionId());
        assertNotNull(newScore, "score drift must keep the member queued for its later due time");
        long expectedNewScore = freshTimestamp.plus(properties.processingTimeout()).toEpochMilli();
        assertEquals(expectedNewScore, newScore.longValue(),
                "score must be re-aligned to the canonical next-retry time read from the session value");
    }

    @Test
    void shouldEmitSameDueSessionOnRepeatedScans_WhenNoServiceUpdateClaimsIt() {
        Session session = sessionWithSignature("v1_svc_repeat", PROCESSING, Instant.now().minus(Duration.ofMinutes(10)));
        seedSession(session);

        List<String> first = new ArrayList<>();
        List<String> second = new ArrayList<>();
        scanner.scanSignatureSessions(filterWithCutoffNow(), first::add);
        scanner.scanSignatureSessions(filterWithCutoffNow(), second::add);

        assertEquals(List.of(session.getSessionId()), first);
        assertEquals(List.of(session.getSessionId()), second);
    }

    private Double score(String queueKey, String sessionId) {
        return stringTemplate.opsForZSet().score(queueKey, sessionId);
    }

    private static SessionStatusReprocessingProperties retryProperties(int maxProcessingAttempts) {
        return new SessionStatusReprocessingProperties(
                maxProcessingAttempts, Duration.ofSeconds(30), Duration.ofSeconds(5));
    }

}
