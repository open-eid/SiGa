package ee.openeid.siga.session.spi;

/**
 * Published after a container session is evicted by the backend's expiry mechanism (Ignite
 * {@code EVT_CACHE_OBJECT_EXPIRED}, Redis keyspace {@code __keyevent@*__:expired}, etc.).
 *
 * <p>Listeners react via {@link org.springframework.context.event.EventListener}. The event is
 * published on the local application node. The Redis-backed publish path runs on the Lettuce
 * pub/sub event loop (see {@code RedisSessionExpiryNotifier}), so listeners that do blocking
 * Redis or JDBC I/O are annotated with {@link org.springframework.scheduling.annotation.Async}
 * and run on Spring's {@code applicationTaskExecutor} instead of the publishing thread.
 *
 * @param sessionId the full session identifier, i.e. {@code cacheVersion_serviceUuid_containerId}
 */
public record ContainerExpiredEvent(String sessionId) {
}
