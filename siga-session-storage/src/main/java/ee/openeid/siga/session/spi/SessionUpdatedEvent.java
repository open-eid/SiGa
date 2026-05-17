package ee.openeid.siga.session.spi;

import ee.openeid.siga.common.session.Session;

/**
 * Published by {@code SessionService.update(Session)} after the session has been persisted. Used by
 * backends (and any other Spring component) that need to react to every session update — for
 * example, a Redis-backed implementation maintaining a secondary status index.
 *
 * <p>Delivered synchronously on the thread that called {@code update}; handlers that need to offload
 * work should opt into {@link org.springframework.scheduling.annotation.Async} explicitly.
 */
public record SessionUpdatedEvent(Session session) {
}
