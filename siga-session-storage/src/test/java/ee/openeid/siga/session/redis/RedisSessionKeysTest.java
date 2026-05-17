package ee.openeid.siga.session.redis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Redis key schema used for session values, keyspace expiry filtering, SCAN-based
 * counting, and reprocessing due queues.
 */
class RedisSessionKeysTest {

    @Test
    void shouldBuildSessionKey_WithCanonicalPrefix() {
        assertEquals("siga:session:v1_svc_abc", RedisSessionKeys.session("v1_svc_abc"));
    }

    @Test
    void shouldRoundTripSessionId_WhenExtractingFromSessionKey() {
        String sessionId = "v1_svc_round_trip";
        String key = RedisSessionKeys.session(sessionId);
        assertEquals(sessionId, RedisSessionKeys.extractSessionId(key));
    }

    @Test
    void shouldRecogniseSessionKey_WhenPrefixMatches() {
        assertTrue(RedisSessionKeys.isSessionKey("siga:session:anything"));
    }

    @Test
    void shouldRejectKeysOutsideSessionNamespace() {
        assertFalse(RedisSessionKeys.isSessionKey(RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE));
        assertFalse(RedisSessionKeys.isSessionKey("siga:lock:{lock}"));
        assertFalse(RedisSessionKeys.isSessionKey(null));
        assertFalse(RedisSessionKeys.isSessionKey(""));
        assertFalse(RedisSessionKeys.isSessionKey("session:v1"));
    }

    @Test
    void shouldReturnNull_WhenExtractingSessionIdFromNonSessionKey() {
        assertNull(RedisSessionKeys.extractSessionId(RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE));
        assertNull(RedisSessionKeys.extractSessionId(null));
    }

    @Test
    void shouldExposeReprocessingDueQueueKeys() {
        // Both due-queue keys share the {reprocess} hashtag so they hash to a single Redis
        // Cluster slot and can be updated together without CROSSSLOT.
        assertEquals("siga:{reprocess}:signature", RedisSessionKeys.SIGNATURE_REPROCESSING_QUEUE);
        assertEquals("siga:{reprocess}:certificate", RedisSessionKeys.CERTIFICATE_REPROCESSING_QUEUE);
    }
}
