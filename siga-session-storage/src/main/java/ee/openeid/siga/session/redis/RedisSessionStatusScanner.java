package ee.openeid.siga.session.redis;

import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.session.configuration.SessionStatusReprocessingProperties;
import ee.openeid.siga.session.redis.ReprocessingScoring.QueueType;
import ee.openeid.siga.session.spi.SessionStatusScanner;
import ee.openeid.siga.session.spi.SessionStorage;
import ee.openeid.siga.session.spi.StatusReprocessingFilter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Read side of the Redis-backed due-time index for the background status reprocessor.
 * Implements {@link SessionStatusScanner} by polling the per-queue ZSETs maintained by
 * {@link RedisSessionEventListener} for members whose score has come due. The canonical session
 * value remains the source of truth; this scanner only consults the index to find candidates and
 * then verifies each one against the canonical value.
 *
 * <p>The index is not a queue in the FIFO/pop sense — entries remain in it until either a session
 * update writes a new score, a scan promotes them to a downstream consumer, or lazy cleanup
 * removes them.
 *
 * <p>{@link #verifyCandidate} is the safety-net lazy cleanup that backstops the fast-path cleanup
 * in {@link RedisSessionEventListener#onContainerExpired onContainerExpired}. Redis pub/sub
 * expiry notifications are best-effort and a dropped event would otherwise leave the ZSET member
 * in place forever; the scanner re-checks every due candidate against the canonical session
 * value and removes orphans or re-aligns stale scores in place. Do not remove these branches
 * without preserving an equivalent fallback on the write side.
 *
 * <p>Scoring math lives in {@link ReprocessingScoring}; this class wires it to Redis ZSET reads
 * and the scanner SPI.
 */
@RequiredArgsConstructor
class RedisSessionStatusScanner implements SessionStatusScanner {
    @NonNull
    private final StringRedisTemplate stringRedisTemplate;
    @NonNull
    private final SessionStorage sessionStorage;
    @NonNull
    private final SessionStatusReprocessingProperties reprocessingProperties;
    private final int batchSize;

    @Override
    public void scanSignatureSessions(StatusReprocessingFilter filter, Consumer<String> sessionIdConsumer) {
        scanQueue(RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE, QueueType.SIGNATURE, filter, sessionIdConsumer);
    }

    @Override
    public void scanCertificateSessions(StatusReprocessingFilter filter, Consumer<String> sessionIdConsumer) {
        scanQueue(RedisSessionKeys.CERTIFICATE_REPROCESSING_QUEUE, QueueType.CERTIFICATE, filter, sessionIdConsumer);
    }

    private void scanQueue(String queueKey, QueueType queueType,
                           StatusReprocessingFilter filter, Consumer<String> sessionIdConsumer) {
        long now = Instant.now().toEpochMilli();
        Set<String> dueSessionIds = stringRedisTemplate.opsForZSet()
                .rangeByScore(queueKey, Double.NEGATIVE_INFINITY, now, 0, batchSize);
        if (dueSessionIds == null) {
            return;
        }
        dueSessionIds.forEach(sessionId -> verifyCandidate(queueKey, queueType, filter, now, sessionId, sessionIdConsumer));
    }

    /**
     * Validates a due-queue candidate against the canonical session value before emitting it.
     * Four short-circuit branches handle distinct failure modes; each one either removes the
     * orphan or re-aligns the score so a future scan stays correct. See the class Javadoc for why
     * the cleanup paths are intentionally redundant with
     * {@link RedisSessionEventListener#onContainerExpired onContainerExpired}.
     */
    private void verifyCandidate(String queueKey, QueueType queueType, StatusReprocessingFilter filter,
                                 long now, @Nullable String sessionId, Consumer<String> sessionIdConsumer) {
        // Defensive guard: ZRANGEBYSCORE should never return null members, but Spring Data Redis
        // surfaces them as nulls in rare edge cases — drop them rather than NPE inside the scan.
        if (sessionId == null) {
            return;
        }
        // Orphan cleanup: the session value is gone (TTL-expired between the ZRANGEBYSCORE batch
        // read and this peek, or pub/sub expiry event was dropped before onContainerExpired could
        // run). Drop the ZSET member so subsequent scans don't re-emit a ghost.
        Optional<Session> maybeSession = sessionStorage.peek(sessionId);
        if (maybeSession.isEmpty()) {
            stringRedisTemplate.opsForZSet().remove(queueKey, sessionId);
            return;
        }

        // Stale-score cleanup: the session exists but no longer has any work that would qualify
        // for this queue (terminal status, retry budget exhausted, all entries are REMOTE).
        // Equivalent to onSessionUpdated having computed score=null but the corresponding ZREM
        // not yet observed by this node — heal in place.
        Session session = maybeSession.get();
        Long currentScore = ReprocessingScoring.scoreFor(session, queueType, reprocessingProperties);
        if (currentScore == null) {
            stringRedisTemplate.opsForZSet().remove(queueKey, sessionId);
            return;
        }
        // Score drift: another writer pushed the next-retry time into the future since this
        // candidate was queued (e.g. the session was just updated with a fresh PROCESSING
        // timestamp). Re-align the ZSET score and skip emission this tick.
        if (currentScore > now) {
            stringRedisTemplate.opsForZSet().add(queueKey, sessionId, currentScore);
            return;
        }
        if (ReprocessingScoring.hasDueWork(session, queueType, filter)) {
            sessionIdConsumer.accept(sessionId);
        }
    }
}
