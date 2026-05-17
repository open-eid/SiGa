package ee.openeid.siga.session.redis;

import org.jspecify.annotations.Nullable;

/**
 * Redis key schema for the session-storage backend.
 *
 * <p>Each session is stored as a single serialized value at {@code siga:session:<sessionId>}.
 * Reads and writes are single-key operations, so they route to one slot on Redis Cluster without
 * any hash-tag braces. Reprocessing due-queue ZSETs use a shared hash tag because the queue
 * membership update scripts touch both keys atomically.
 */
final class RedisSessionKeys {

    static final String SESSION_PREFIX = "siga:session:";
    static final String SESSION_SCAN_PATTERN = SESSION_PREFIX + "*";

    // The {reprocess} hashtag forces both due-queue ZSETs into the same Redis Cluster slot so the
    // cross-key Lua scripts in RedisSessionEventListener (onSessionUpdated, removeFromAllQueues)
    // execute atomically without CROSSSLOT errors. Single-slot concentration is acceptable: the
    // queues hold one ZSET entry per active reprocessable session — tiny next to the per-session
    // values which fan out across slots normally.
    static final String SIGNATURE_REPROCESSING_QUEUE = "siga:{reprocess}:signature";
    static final String CERTIFICATE_REPROCESSING_QUEUE = "siga:{reprocess}:certificate";

    private RedisSessionKeys() {
    }

    static String session(String sessionId) {
        return SESSION_PREFIX + sessionId;
    }

    static boolean isSessionKey(@Nullable String key) {
        return key != null && key.startsWith(SESSION_PREFIX);
    }

    static @Nullable String extractSessionId(@Nullable String sessionKey) {
        if (sessionKey == null || !isSessionKey(sessionKey)) {
            return null;
        }
        return sessionKey.substring(SESSION_PREFIX.length());
    }
}
