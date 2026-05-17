package ee.openeid.siga.session.spi;

/**
 * Published by {@code SessionService.removeBySessionId(String)} after the session payload has been
 * deleted from the backend. Distinct from {@link ContainerExpiredEvent}, which fires only on
 * TTL-driven eviction — this event covers the explicit-delete path (e.g., an API client deleting
 * their container, or finalize cleanup).
 *
 * <p>Listeners react via {@link org.springframework.context.event.EventListener}; on the Redis
 * backend, the reprocessing due queue consumes it to trim queued work, since Redis does not
 * publish {@code __keyevent@*__:expired} for explicit {@code DEL}s.
 *
 * <p>Delivered synchronously on the thread that called {@code removeBySessionId}.
 *
 * @param sessionId the full session identifier, i.e. {@code cacheVersion_serviceUuid_containerId}
 */
public record SessionRemovedEvent(String sessionId) {
}
