package ee.openeid.siga.session.redis;

import ee.openeid.siga.common.model.SigningType;
import ee.openeid.siga.common.session.CertificateSession;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.session.SessionStatus;
import ee.openeid.siga.common.session.SignatureSession;
import ee.openeid.siga.session.configuration.SessionStatusReprocessingProperties;
import ee.openeid.siga.session.spi.StatusReprocessingFilter;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Pure scoring math for the Redis-backed reprocessing due index. Given a session and the
 * reprocessing properties, produces the next-due epoch-millis score per queue type; given a
 * filter, decides whether a session has work that currently matches the cutoff. Holds no Redis
 * client state so each rule remains isolated and deterministic.
 */
final class ReprocessingScoring {

    private ReprocessingScoring() {
    }

    static Scores scoresForSession(Session session, SessionStatusReprocessingProperties properties) {
        return new Scores(
                scoreFor(session, QueueType.SIGNATURE, properties),
                scoreFor(session, QueueType.CERTIFICATE, properties));
    }

    static @Nullable Long scoreFor(Session session, QueueType queueType, SessionStatusReprocessingProperties properties) {
        return queueType.statuses(session)
                .filter(status -> isQueueEligible(status, properties.maxProcessingAttempts()))
                .map(status -> nextRetryAt(status, properties))
                .min(Long::compare)
                .orElse(null);
    }

    static boolean hasDueWork(Session session, QueueType queueType, StatusReprocessingFilter filter) {
        return queueType.statuses(session).anyMatch(status -> matchesFilter(status, filter));
    }

    private static boolean isQueueEligible(@Nullable SessionStatus status, long maxProcessingAttempts) {
        return status != null && status.isPendingReprocessing(maxProcessingAttempts);
    }

    private static boolean matchesFilter(@Nullable SessionStatus status, StatusReprocessingFilter filter) {
        return status != null
                && status.isDueForReprocessing(filter.processingCutoff(), filter.exceptionCutoff(),
                filter.maxProcessingRetries());
    }

    private static long nextRetryAt(SessionStatus status, SessionStatusReprocessingProperties properties) {
        Instant dueAt = switch (status.getProcessingStatus()) {
            case PROCESSING -> status.getProcessingStatusTimestamp().plus(properties.processingTimeout());
            case EXCEPTION -> status.getProcessingStatusTimestamp().plus(properties.exceptionTimeout());
            default ->
                    throw new IllegalArgumentException("Unsupported reprocessing status: " + status.getProcessingStatus());
        };
        return dueAt.toEpochMilli();
    }

    enum QueueType {
        SIGNATURE(session -> values(session.getSignatureSessions())
                .filter(signature -> isPollable(signature.getSigningType()))
                .map(SignatureSession::getSessionStatus)),
        CERTIFICATE(session -> values(session.getCertificateSessions())
                .map(CertificateSession::getSessionStatus));

        private final Function<Session, Stream<SessionStatus>> statuses;

        QueueType(Function<Session, Stream<SessionStatus>> statuses) {
            this.statuses = statuses;
        }

        Stream<SessionStatus> statuses(Session session) {
            return statuses.apply(session);
        }

        private static <T extends Object> Stream<T> values(
                @Nullable Map<String, ? extends @Nullable T> sessions) {
            if (sessions == null) {
                return Stream.empty();
            }

            return sessions.values().stream().<T>mapMulti((value, output) -> {
                if (value != null) {
                    output.accept(value);
                }
            });
        }

        private static boolean isPollable(@Nullable SigningType signingType) {
            return signingType == SigningType.SMART_ID || signingType == SigningType.MOBILE_ID;
        }
    }

    record Scores(@Nullable Long signature, @Nullable Long certificate) {
    }
}
