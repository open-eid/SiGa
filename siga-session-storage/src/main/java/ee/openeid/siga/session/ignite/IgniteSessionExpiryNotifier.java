package ee.openeid.siga.session.ignite;

import ee.openeid.siga.session.spi.ContainerExpiredEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteEvents;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.events.CacheEvent;
import org.apache.ignite.events.EventType;
import org.jspecify.annotations.Nullable;
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
    @NonNull
    private final Ignite ignite;
    @NonNull
    private final ApplicationEventPublisher eventPublisher;
    private @Nullable IgniteEvents expiryEvents;
    private @Nullable UUID expiryListenerId;

    @PostConstruct
    public void registerExpiryListener() {
        expiryEvents = ignite.events(ignite.cluster().forCacheNodes(CacheName.CONTAINER_SESSION.name()));
        expiryListenerId = expiryEvents.remoteListen(
                (UUID uuid, CacheEvent event) -> {
                    log.info("CACHE_OBJECT_EXPIRED received: cacheName={}, key={}", event.cacheName(), event.key());
                    if (CacheName.CONTAINER_SESSION.name().equals(event.cacheName())) {
                        if (event.oldValue() instanceof BinaryObject binaryObject) {
                            log.debug("Old value is a BinaryObject, extracting sessionId");
                            String sessionId = extractSessionId(binaryObject);
                            if (sessionId == null) {
                                log.warn("Ignoring expired container session without a sessionId: key={}",
                                        String.valueOf(event.key()));
                                return true;
                            }
                            eventPublisher.publishEvent(new ContainerExpiredEvent(sessionId));
                        }
                    }
                    return true;
                }, null, EventType.EVT_CACHE_OBJECT_EXPIRED);
    }

    @PreDestroy
    public void unregisterExpiryListener() {
        IgniteEvents events = expiryEvents;
        UUID listenerId = expiryListenerId;
        if (events == null || listenerId == null) {
            return;
        }
        try {
            events.stopRemoteListen(listenerId);
        } catch (RuntimeException e) {
            log.warn("Failed to stop Ignite session expiry event listener", e);
        } finally {
            expiryListenerId = null;
            expiryEvents = null;
        }
    }

    private static @Nullable String extractSessionId(BinaryObject sessionObject) {
        return sessionObject.field("sessionId");
    }
}
