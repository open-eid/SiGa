package ee.openeid.siga.session.spi;

import ee.openeid.siga.common.session.Session;

import java.util.Optional;

/**
 * Backend-agnostic contract for persisting SiGa container sessions. A session is a composite of a
 * {@link Session} (the container) plus its signature and certificate session maps, all keyed by the
 * same {@code sessionId} ({@code cacheVersion_serviceUuid_containerId}).
 *
 * <p>Implementations must be thread-safe: {@link #get}, {@link #update}, and {@link #remove} can be
 * called concurrently from controller threads, async MID/SID polling tasks, and the status
 * reprocessor. Coordination between writers on the same {@code sessionId} is provided by
 * {@link SessionLockRegistry}, not by this interface.
 */
public interface SessionStorage {

    /**
     * Reads the session and refreshes its idle TTL. Use from user-driven paths (HTTP request
     * handlers, signing flows) where activity should keep the session alive.
     *
     * @return the session for {@code sessionId} with its signature and certificate sessions
     * attached, or {@link Optional#empty()} if no container exists under that key
     */
    Optional<Session> get(String sessionId);

    /**
     * Reads the session without refreshing its TTL. Use from background paths (status
     * reprocessor, compactors) that must not keep otherwise-idle sessions alive — otherwise a
     * session stuck in {@code PROCESSING} would have its TTL refreshed on every reprocessor
     * cycle and never expire.
     *
     * @return the session for {@code sessionId} with its signature and certificate sessions
     * attached, or {@link Optional#empty()} if no container exists under that key
     */
    Optional<Session> peek(String sessionId);

    /**
     * Persists the container and replaces its signature / certificate session maps. Writers are
     * serialized via {@link SessionLockRegistry}.
     */
    void update(Session session);

    /**
     * Removes the container and its associated signature / certificate session maps.
     */
    void remove(String sessionId);

    long size();
}
