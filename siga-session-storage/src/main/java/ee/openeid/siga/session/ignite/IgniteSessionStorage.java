package ee.openeid.siga.session.ignite;

import ee.openeid.siga.common.session.CertificateSession;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.session.SignatureSession;
import ee.openeid.siga.session.spi.SessionStorage;
import lombok.RequiredArgsConstructor;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CachePeekMode;

import javax.cache.expiry.Duration;
import javax.cache.expiry.ExpiryPolicy;
import javax.cache.expiry.ModifiedExpiryPolicy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Ignite implementation of the {@link SessionStorage} SPI. A container session is split across the
 * three caches named by {@link CacheName}: the container itself in {@code CONTAINER_SESSION} and
 * the per-container signature / certificate session maps in {@code SIGNATURE_SESSION} and
 * {@code CERTIFICATE_SESSION}. The split exists so the container cache can carry a
 * {@code TouchedExpiryPolicy} (read activity slides idle TTL) while the sub-maps can be configured
 * independently, and so the {@code SIGNATURE_SESSION} cache can be queried with a binary,
 * field-addressable layout — required by {@link SignatureStatusRequestFilter} server-side and
 * produced by {@code SignatureSession}'s {@link org.apache.ignite.binary.Binarylizable}
 * implementation.
 *
 * <p>{@link #get} refreshes the container's idle TTL through the cache-level expiry policy;
 * {@link #peek} overrides it with {@link #NO_TOUCH_ON_ACCESS} so background scanners (the status
 * reprocessor) don't keep otherwise-idle sessions alive. See the {@link #NO_TOUCH_ON_ACCESS} field
 * javadoc for the JCache details.
 */
@RequiredArgsConstructor
public class IgniteSessionStorage implements SessionStorage {

    /**
     * Per-operation expiry override used by {@link #peek}. {@code ModifiedExpiryPolicy} returns
     * {@code null} from {@code getExpiryForAccess()}, which the JCache contract interprets as
     * "leave the entry's TTL unchanged on read". Applying it via {@code withExpiryPolicy} cancels
     * out the {@code TouchedExpiryPolicy} that {@code CONTAINER_SESSION} is configured with —
     * background reads no longer slide the TTL. {@code SIGNATURE_SESSION} and
     * {@code CERTIFICATE_SESSION} already use {@code ModifiedExpiryPolicy} so the override is a
     * no-op for them; we apply it uniformly to keep the semantics symmetric.
     */
    private static final ExpiryPolicy NO_TOUCH_ON_ACCESS = new ModifiedExpiryPolicy(Duration.ETERNAL);

    private final Ignite ignite;

    @Override
    public Optional<Session> get(String sessionId) {
        return readSession(sessionId, false);
    }

    @Override
    public Optional<Session> peek(String sessionId) {
        return readSession(sessionId, true);
    }

    private Optional<Session> readSession(String sessionId, boolean suppressTouch) {
        @SuppressWarnings("resource")
        IgniteCache<String, Session> containerCache = suppressTouch
                ? getContainerCache().withExpiryPolicy(NO_TOUCH_ON_ACCESS)
                : getContainerCache();
        Session container = containerCache.get(sessionId);
        if (container == null) {
            return Optional.empty();
        }
        container.setSignatureSessions(Optional
                .ofNullable(getSignatureSessionCache().get(sessionId))
                .orElseGet(HashMap::new));
        container.setCertificateSessions(Optional
                .ofNullable(getCertificateSessionCache().get(sessionId))
                .orElseGet(HashMap::new));
        return Optional.of(container);
    }

    @Override
    public void update(Session session) {
        getContainerCache().put(session.getSessionId(), session);
        getSignatureSessionCache().put(session.getSessionId(), session.getSignatureSessions());
        getCertificateSessionCache().put(session.getSessionId(), session.getCertificateSessions());
    }

    @Override
    public void remove(String sessionId) {
        getContainerCache().remove(sessionId);
        getSignatureSessionCache().remove(sessionId);
        getCertificateSessionCache().remove(sessionId);
    }

    /**
     * Cluster-wide unique count of container sessions. Uses {@code CachePeekMode.PRIMARY}
     * so each entry is counted once — {@code CachePeekMode.ALL} would aggregate primary and
     * backup partitions, double-reporting under {@code backups=1}.
     */
    @Override
    public long size() {
        return ignite.cache(CacheName.CONTAINER_SESSION.name()).sizeLong(CachePeekMode.PRIMARY);
    }

    private IgniteCache<String, Session> getContainerCache() {
        return ignite.getOrCreateCache(CacheName.CONTAINER_SESSION.name());
    }

    private IgniteCache<String, Map<String, SignatureSession>> getSignatureSessionCache() {
        return ignite.getOrCreateCache(CacheName.SIGNATURE_SESSION.name());
    }

    private IgniteCache<String, Map<String, CertificateSession>> getCertificateSessionCache() {
        return ignite.getOrCreateCache(CacheName.CERTIFICATE_SESSION.name());
    }
}
