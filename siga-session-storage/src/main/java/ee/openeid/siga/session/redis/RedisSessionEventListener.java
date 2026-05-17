package ee.openeid.siga.session.redis;

import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.session.configuration.SessionStatusReprocessingProperties;
import ee.openeid.siga.session.redis.ReprocessingScoring.Scores;
import ee.openeid.siga.session.spi.ContainerExpiredEvent;
import ee.openeid.siga.session.spi.SessionRemovedEvent;
import ee.openeid.siga.session.spi.SessionUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Async;

import java.util.List;

/**
 * Write side of the Redis-backed due-time index for the background status reprocessor. Listens
 * to session lifecycle events and maintains the per-queue ZSET membership so {@link
 * RedisSessionStatusScanner} can find due candidates without scanning the full session keyspace.
 * The canonical session value (stored by {@link RedisSessionStorage}) remains the source of
 * truth; this index only stores the next epoch-millis at which a container session should be
 * considered for reprocessing.
 *
 * <p>{@link #onContainerExpired} is the fast-path cleanup driven by Redis keyspace expiry
 * notifications. It is intentionally redundant with the lazy-cleanup branches inside
 * {@link RedisSessionStatusScanner#verifyCandidate verifyCandidate} — Redis pub/sub is
 * best-effort and a dropped expiry event would otherwise leak ZSET members forever. Do not
 * remove this fast path without preserving the lazy backstop on the read side.
 *
 * <p>Scoring math lives in {@link ReprocessingScoring}; this class wires it to Redis ZSET
 * mutations and Spring event listeners.
 */
@RequiredArgsConstructor
class RedisSessionEventListener {

    /**
     * Atomic ZADD-or-ZREM across both due queues in a single Lua frame. The keys share the
     * {@code {reprocess}} hashtag (see {@link RedisSessionKeys}) so they route to one cluster slot
     * and the script avoids {@code CROSSSLOT}; either both queues are updated or neither is.
     *
     * <p>KEYS[1] = signature queue, KEYS[2] = certificate queue;<br>
     * ARGV[1] = sessionId, ARGV[2] = signature score (empty = remove), ARGV[3] = certificate
     * score (empty = remove).
     */
    private static final RedisScript<Long> APPLY_DUE_MEMBERSHIP_SCRIPT = new DefaultRedisScript<>(
            """
                    if ARGV[2] == '' then
                        redis.call('ZREM', KEYS[1], ARGV[1])
                    else
                        redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])
                    end
                    if ARGV[3] == '' then
                        redis.call('ZREM', KEYS[2], ARGV[1])
                    else
                        redis.call('ZADD', KEYS[2], ARGV[3], ARGV[1])
                    end
                    return 1
                    """,
            Long.class);

    /**
     * Atomic ZREM across both due queues — same hashtag/atomicity contract as
     * {@link #APPLY_DUE_MEMBERSHIP_SCRIPT}.
     *
     * <p>KEYS[1] = signature queue, KEYS[2] = certificate queue; ARGV[1] = sessionId.
     */
    private static final RedisScript<Long> REMOVE_FROM_QUEUES_SCRIPT = new DefaultRedisScript<>(
            """
                    redis.call('ZREM', KEYS[1], ARGV[1])
                    redis.call('ZREM', KEYS[2], ARGV[1])
                    return 1
                    """,
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final SessionStatusReprocessingProperties reprocessingProperties;

    /**
     * Runs synchronously on the publishing thread so update/remove ordering from
     * {@code SessionService} is preserved in the due queues.
     */
    @EventListener
    public void onSessionUpdated(SessionUpdatedEvent event) {
        Session session = event.session();
        String sessionId = session.getSessionId();
        Scores scores = ReprocessingScoring.scoresForSession(session, reprocessingProperties);

        stringRedisTemplate.execute(
                APPLY_DUE_MEMBERSHIP_SCRIPT,
                List.of(RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE,
                        RedisSessionKeys.CERTIFICATE_REPROCESSING_QUEUE),
                sessionId,
                scoreOrRemoveMarker(scores.signature()),
                scoreOrRemoveMarker(scores.certificate()));
    }

    /**
     * Lua sentinel: empty string means "remove", any other string is the score to ZADD.
     */
    private static String scoreOrRemoveMarker(@Nullable Long score) {
        return score == null ? "" : Long.toString(score);
    }

    /**
     * Fast-path cleanup for sessions that expired via Redis TTL. Events arrive from Lettuce
     * pub/sub threads; Redis cleanup is offloaded to avoid blocking the event loop. Correctness
     * does not depend on this path — {@link RedisSessionStatusScanner#verifyCandidate
     * verifyCandidate} also cleans stale due entries lazily during every scan, which is the
     * defence against dropped pub/sub notifications.
     */
    @Async
    @EventListener
    public void onContainerExpired(ContainerExpiredEvent event) {
        removeFromAllQueues(event.sessionId());
    }

    /**
     * Runs synchronously rather than {@code @Async} to avoid a delayed-ZREM hazard after explicit
     * delete followed by a fresh update for the same session id.
     */
    @EventListener
    public void onSessionRemoved(SessionRemovedEvent event) {
        removeFromAllQueues(event.sessionId());
    }

    private void removeFromAllQueues(@Nullable String sessionId) {
        if (sessionId == null) {
            return;
        }
        stringRedisTemplate.execute(
                REMOVE_FROM_QUEUES_SCRIPT,
                List.of(RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE,
                        RedisSessionKeys.CERTIFICATE_REPROCESSING_QUEUE),
                sessionId);
    }
}
