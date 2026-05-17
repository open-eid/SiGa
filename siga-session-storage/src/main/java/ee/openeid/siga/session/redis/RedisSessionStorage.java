package ee.openeid.siga.session.redis;

import ee.openeid.siga.common.session.CertificateSession;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.session.SignatureSession;
import ee.openeid.siga.session.spi.SessionStorage;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

import java.io.Serializable;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed {@link SessionStorage}.
 *
 * <p>Each session is a single serialized value at {@code siga:session:<sessionId>}. The stored
 * value contains the container and the transient signature/certificate session maps that the
 * container implementations deliberately do not serialize themselves. Single-key operations route
 * by slot on Redis Cluster without any hash-tag braces or multi-key SCAN coordination.
 *
 * <p>Read paths are split: {@link #get} uses Redis {@code GETEX} semantics to refresh the idle TTL
 * for user-driven activity, while {@link #peek} reads without refreshing healthy sessions. Peek
 * still heals a readable value with {@code TTL == -1}, so an out-of-band write cannot leak forever.
 *
 * <p>Writes use a single Redis {@code SET key value EX ttl} style operation through Spring Data
 * Redis, so the value and expiry are applied together without custom scripting.
 */
@RequiredArgsConstructor
class RedisSessionStorage implements SessionStorage {

    private static final long SCAN_COUNT_HINT = 1000L;

    @NonNull
    private final RedisTemplate<String, Object> redisTemplate;
    @NonNull
    private final Duration sessionTtl;

    @Override
    public Optional<Session> get(String sessionId) {
        return readSession(sessionId, true);
    }

    @Override
    public Optional<Session> peek(String sessionId) {
        return readSession(sessionId, false);
    }

    private Optional<Session> readSession(String sessionId, boolean refreshTtl) {
        String key = RedisSessionKeys.session(sessionId);
        StoredSession stored = refreshTtl
                ? (StoredSession) redisTemplate.opsForValue().getAndExpire(key, sessionTtl)
                : (StoredSession) redisTemplate.opsForValue().get(key);
        if (stored == null) {
            return Optional.empty();
        }
        Long ttl = refreshTtl ? null : redisTemplate.getExpire(key, TimeUnit.SECONDS);
        // Orphan healing: a readable value with no TTL must not survive the next read. update()
        // uses SET-with-expiry so SiGA writers cannot create one, but a direct write (operator
        // action, foreign tool) still can. Applying sessionTtl here turns the orphan back into a
        // normally-expiring session without refreshing healthy background reads.
        if (ttl != null && ttl == -1L) {
            redisTemplate.expire(key, sessionTtl);
        }
        return Optional.of(stored.toSession());
    }

    @Override
    public void update(Session session) {
        String key = RedisSessionKeys.session(session.getSessionId());
        redisTemplate.opsForValue().set(key, StoredSession.from(session), sessionTtl);
    }

    @Override
    public void remove(String sessionId) {
        redisTemplate.delete(RedisSessionKeys.session(sessionId));
    }

    /**
     * Counts session keys across the deployment. The result is approximate during topology
     * changes (slots migrating mid-scan can be counted twice or missed); acceptable since this
     * number feeds operational monitoring, not business logic.
     */
    @Override
    public long size() {
        Long count = redisTemplate.execute((RedisCallback<Long>) connection -> {
            ScanOptions options = ScanOptions.scanOptions()
                    .match(RedisSessionKeys.SESSION_SCAN_PATTERN)
                    .count(SCAN_COUNT_HINT)
                    .build();
            long[] total = {0L};
            RedisKeyScan.forEachKey(connection, options, key -> total[0]++);
            return total[0];
        });
        return count != null ? count : 0L;
    }

    /**
     * Serialization wrapper that is needed for as long as the {@link Session} implementations
     * declare their signature/certificate session maps {@code transient} (the Ignite backend
     * keeps those maps in separate caches). JDK-serializing the bare container would silently
     * drop them, so this wrapper carries the maps as explicit components and reattaches them
     * on read.
     */
    private record StoredSession(
            Session container,
            Map<String, SignatureSession> signatures,
            Map<String, CertificateSession> certificates) implements Serializable {

        private static final long serialVersionUID = 1L;

        static StoredSession from(Session session) {
            return new StoredSession(
                    session,
                    copyOrEmpty(session.getSignatureSessions()),
                    copyOrEmpty(session.getCertificateSessions()));
        }

        private Session toSession() {
            container.setSignatureSessions(copyOrEmpty(signatures));
            container.setCertificateSessions(copyOrEmpty(certificates));
            return container;
        }

        private static <T> Map<String, T> copyOrEmpty(@Nullable Map<String, T> source) {
            return source != null ? new HashMap<>(source) : new HashMap<>();
        }
    }
}
