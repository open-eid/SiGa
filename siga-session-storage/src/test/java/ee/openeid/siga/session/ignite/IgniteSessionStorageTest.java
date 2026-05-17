package ee.openeid.siga.session.ignite;

import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.session.spi.SessionStorage;
import ee.openeid.siga.session.spi.SessionStorageTest;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.cache.expiry.Duration;
import javax.cache.expiry.ModifiedExpiryPolicy;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ignite-backed contract tests plus backend-specific TTL assertions. The shared SPI contract is
 * inherited from {@link SessionStorageTest}; the JCache API doesn't expose an
 * entry's remaining TTL directly (unlike Redis's {@code TTL} command), so the TTL behaviour tests
 * shorten the {@code CONTAINER_SESSION} entry's TTL to a known small window and assert via
 * expiry: if {@link IgniteSessionStorage#peek} doesn't slide TTL, the entry is gone after the
 * window elapses; if {@link IgniteSessionStorage#get} does slide TTL, the entry survives past it.
 */
class IgniteSessionStorageTest extends SessionStorageTest {

    private static final long SHORT_TTL_SECONDS = 2L;

    private static Ignite ignite;
    private static IgniteSessionStorage storage;

    @BeforeAll
    static void startIgnite() {
        System.setProperty("IGNITE_OVERRIDE_CONSISTENT_ID", "node00");
        ignite = Ignition.start("ignite-test-configuration.xml");
        storage = new IgniteSessionStorage(ignite);
    }

    @AfterAll
    static void stopIgnite() {
        if (ignite != null) {
            ignite.close();
        }
    }

    @Override
    protected SessionStorage storage() {
        return storage;
    }

    @Override
    protected void resetStorage() {
        ignite.cache(CacheName.CONTAINER_SESSION.name()).clear();
        ignite.cache(CacheName.SIGNATURE_SESSION.name()).clear();
        ignite.cache(CacheName.CERTIFICATE_SESSION.name()).clear();
    }

    @Test
    void shouldSetTtl_WhenUpdateCalled() throws InterruptedException {
        // JCache doesn't expose an entry's remaining TTL, so we verify behaviorally:
        // pre-populate with a deliberately short TTL, then storage.update() must reset
        // the entry's TTL back to the cache's configured idle window via the default
        // TouchedExpiryPolicy's getExpiryForUpdate. If update() failed to apply a TTL,
        // the entry would expire within the short window and the containsKey check
        // below would fail.
        String sessionId = "v1_svc_set_ttl";
        Session session = newSession(sessionId);
        shortenContainerCacheTtl(sessionId, session);

        storage.update(session);

        TimeUnit.MILLISECONDS.sleep(2_700);

        assertTrue(containerCache().containsKey(sessionId),
                "update() must reset TTL to the cache's configured idle window, " +
                        "not preserve a previously-shortened TTL");
    }

    @Test
    void shouldNotRefreshTtl_WhenPeekCalled() throws InterruptedException {
        String sessionId = "v1_svc_peek_ttl";
        Session session = newSession(sessionId);
        storage.update(session);
        shortenContainerCacheTtl(sessionId, session);

        TimeUnit.MILLISECONDS.sleep(1_200);

        var peeked = storage.peek(sessionId);
        assertTrue(peeked.isPresent(), "peek should still find the entry while TTL is alive");
        assertEquals(sessionId, peeked.get().getSessionId());

        TimeUnit.MILLISECONDS.sleep(1_500);

        assertFalse(containerCache().containsKey(sessionId),
                "peek must not slide TTL — entry should have expired by now");
    }

    @Test
    void shouldRefreshTtl_WhenGetCalled() throws InterruptedException {
        String sessionId = "v1_svc_get_ttl";
        Session session = newSession(sessionId);
        storage.update(session);
        shortenContainerCacheTtl(sessionId, session);

        TimeUnit.MILLISECONDS.sleep(1_200);

        var got = storage.get(sessionId);
        assertTrue(got.isPresent(), "get should still find the entry while TTL is alive");

        TimeUnit.MILLISECONDS.sleep(1_500);

        assertTrue(containerCache().containsKey(sessionId),
                "get must slide TTL via TouchedExpiry — entry should survive past the original window");
    }

    private void shortenContainerCacheTtl(String sessionId, Session session) {
        containerCache()
                .withExpiryPolicy(new ModifiedExpiryPolicy(new Duration(TimeUnit.SECONDS, SHORT_TTL_SECONDS)))
                .put(sessionId, session);
    }

    private IgniteCache<String, Session> containerCache() {
        return ignite.cache(CacheName.CONTAINER_SESSION.name());
    }
}
