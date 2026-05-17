package ee.openeid.siga.session.ignite;

import ee.openeid.siga.session.spi.ContainerExpiredEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteEvents;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.events.CacheEvent;
import org.apache.ignite.events.EventType;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

/**
 * Republishes Ignite {@code EVT_CACHE_OBJECT_EXPIRED} events on the container session cache as
 * {@link ContainerExpiredEvent}. {@code ContainerConnectionCleanupListener} reacts via
 * {@code @EventListener} to delete the auth connection rows tied to the expired session.
 */
@Slf4j
@RequiredArgsConstructor
public class IgniteSessionExpiryNotifier {
    private final Ignite ignite;
    private final ApplicationEventPublisher eventPublisher;
    private IgniteEvents expiryEvents;
    private UUID expiryListenerId;

    @PostConstruct
    public void registerExpiryListener() {
        expiryEvents = ignite.events(ignite.cluster().forCacheNodes(CacheName.CONTAINER_SESSION.name()));
        expiryListenerId = expiryEvents.remoteListen(
                (UUID uuid, CacheEvent event) -> {
                    log.info("CACHE_OBJECT_EXPIRED received: cacheName={}, key={}", event.cacheName(), event.key());
                    if (CacheName.CONTAINER_SESSION.name().equals(event.cacheName())) {
                        eventPublisher.publishEvent(new ContainerExpiredEvent(extractSessionId((BinaryObject) event.oldValue())));
                    }
                    return true;
                }, null, EventType.EVT_CACHE_OBJECT_EXPIRED);
    }

    @PreDestroy
    public void unregisterExpiryListener() {
        if (expiryListenerId == null) {
            return;
        }
        try {
            expiryEvents.stopRemoteListen(expiryListenerId);
        } catch (RuntimeException e) {
            log.warn("Failed to stop Ignite session expiry event listener", e);
        } finally {
            expiryListenerId = null;
            expiryEvents = null;
        }
    }

    private static String extractSessionId(BinaryObject sessionObject) {
        return sessionObject.field("sessionId");
    }
}
