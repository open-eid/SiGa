package ee.openeid.siga.session.ignite;

import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.session.spi.ContainerExpiredEvent;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import javax.cache.expiry.Duration;
import javax.cache.expiry.ModifiedExpiryPolicy;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@link IgniteSessionExpiryNotifier} is required: it bridges Ignite's
 * {@code EVT_CACHE_OBJECT_EXPIRED} into the Spring application context as a
 * {@link ContainerExpiredEvent}. Without this bridge, {@code ContainerConnectionCleanupListener}
 * never fires on TTL eviction and auth connection rows accumulate. The test puts a session with a
 * short {@code ModifiedExpiryPolicy} and asserts the published Spring event reaches a recording
 * {@code ApplicationEventPublisher}.
 */
class IgniteSessionExpiryNotifierTest {

    private static Ignite ignite;
    private final List<Object> publishedEvents = new CopyOnWriteArrayList<>();
    private final ApplicationEventPublisher recordingPublisher = (ApplicationEventPublisher) publishedEvents::add;

    @BeforeAll
    static void startIgnite() {
        System.setProperty("IGNITE_OVERRIDE_CONSISTENT_ID", "node00");
        ignite = Ignition.start("ignite-test-configuration.xml");
    }

    @AfterAll
    static void stopIgnite() {
        if (ignite != null) {
            ignite.close();
        }
    }

    @BeforeEach
    void resetState() {
        publishedEvents.clear();
        ignite.cache(CacheName.CONTAINER_SESSION.name()).clear();
    }

    @Test
    void shouldPublishContainerExpiredEvent_WhenSessionEntryExpires() {
        IgniteSessionExpiryNotifier notifier = new IgniteSessionExpiryNotifier(ignite, recordingPublisher);
        notifier.registerExpiryListener();

        try {
            String sessionId = "v1_svc_expiry_notifier";
            putWithShortTtl(sessionId, 1L);

            Awaitility.await()
                    .atMost(10, TimeUnit.SECONDS)
                    .pollInterval(200, TimeUnit.MILLISECONDS)
                    .until(() -> publishedEvents.stream().anyMatch(ContainerExpiredEvent.class::isInstance));

            List<ContainerExpiredEvent> expired = publishedEvents.stream()
                    .filter(ContainerExpiredEvent.class::isInstance)
                    .map(ContainerExpiredEvent.class::cast)
                    .toList();
            assertEquals(1, expired.size(), "Exactly one ContainerExpiredEvent should be published");
            assertEquals(sessionId, expired.get(0).sessionId(),
                    "The published event must carry the sessionId extracted from the BinaryObject");
        } finally {
            notifier.unregisterExpiryListener();
        }
    }

    @Test
    void shouldNotPublishEvent_WhenListenerIsUnregistered() {
        IgniteSessionExpiryNotifier notifier = new IgniteSessionExpiryNotifier(ignite, recordingPublisher);
        notifier.registerExpiryListener();
        notifier.unregisterExpiryListener();

        putWithShortTtl("v1_svc_unregistered_listener", 1L);

        assertNoContainerExpiredEventPublished();
    }

    @Test
    void shouldNotPublishEvent_WhenListenerNotRegistered() {
        // Negative control: without registerExpiryListener(), the EVT_CACHE_OBJECT_EXPIRED still
        // fires on the cache, but no Spring event is published. Pins the @PostConstruct
        // registration as the load-bearing line.
        new IgniteSessionExpiryNotifier(ignite, recordingPublisher);

        putWithShortTtl("v1_svc_no_listener", 1L);

        assertNoContainerExpiredEventPublished();
    }

    private void assertNoContainerExpiredEventPublished() {
        // Give time for the entry to expire — without a registered listener we should see no event.
        Awaitility.await()
                .pollDelay(2, TimeUnit.SECONDS)
                .atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(publishedEvents.isEmpty()
                                || publishedEvents.stream().noneMatch(ContainerExpiredEvent.class::isInstance),
                        "No ContainerExpiredEvent expected without registerExpiryListener()"));
    }

    private void putWithShortTtl(String sessionId, long seconds) {
        Session session = HashcodeContainerSession.builder()
                .sessionId(sessionId)
                .clientName("client").serviceName("svc").serviceUuid("uuid")
                .dataFiles(Collections.emptyList())
                .signatures(Collections.emptyList())
                .build();
        IgniteCache<String, Session> cache = ignite.cache(CacheName.CONTAINER_SESSION.name());
        cache.withExpiryPolicy(new ModifiedExpiryPolicy(new Duration(TimeUnit.SECONDS, seconds)))
                .put(sessionId, session);
    }
}
