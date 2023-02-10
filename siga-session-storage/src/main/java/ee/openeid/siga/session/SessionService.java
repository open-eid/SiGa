package ee.openeid.siga.session;

import ee.openeid.siga.common.exception.ResourceNotFoundException;
import ee.openeid.siga.common.session.CertificateSession;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.session.SignatureSession;
import ee.openeid.siga.session.configuration.SessionConfigurationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.cache.CachePeekMode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.cache.Cache;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@EnableConfigurationProperties({SessionConfigurationProperties.class})
@RequiredArgsConstructor
public class SessionService {
    private final Ignite ignite;
    private final SessionConfigurationProperties sessionConfigurationProperties;

    public Session getContainer(String containerId) {
        String sessionId = getSessionId(containerId);
        return getContainerBySessionId(sessionId);
    }

    public Session getContainerBySessionId(String sessionId) {
        Session container = Optional.ofNullable(getContainerCache().get(sessionId))
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        log.info("Found container with container ID [{}]", container.getSessionId());
        container.setSignatureSessions(Optional
                .ofNullable(getSignatureSessionCache().get(sessionId))
                .orElseGet(HashMap::new));
        container.setCertificateSessions(Optional
                .ofNullable(getCertificateSessionCache().get(sessionId))
                .orElseGet(HashMap::new));
        return container;
    }

    public void update(Session session) {
        getContainerCache().put(session.getSessionId(), session);
        getSignatureSessionCache().put(session.getSessionId(), session.getSignatureSessions());
        getCertificateSessionCache().put(session.getSessionId(), session.getCertificateSessions());
    }

    public void remove(String containerId) {
        String sessionId = getSessionId(containerId);
        removeBySessionId(sessionId);
    }

    public void removeBySessionId(String sessionId) {
        getContainerCache().remove(sessionId);
        getSignatureSessionCache().remove(sessionId);
        getCertificateSessionCache().remove(sessionId);
    }

    private Cache<String, Session> getContainerCache() {
        return ignite.getOrCreateCache(CacheName.CONTAINER_SESSION.name());
    }

    private Cache<String, Map<String, SignatureSession>> getSignatureSessionCache() {
        return ignite.getOrCreateCache(CacheName.SIGNATURE_SESSION.name());
    }

    private Cache<String, Map<String, CertificateSession>> getCertificateSessionCache() {
        return ignite.getOrCreateCache(CacheName.CERTIFICATE_SESSION.name());
    }

    public int getCacheSize() {
        return ignite.cache(CacheName.CONTAINER_SESSION.name()).size(CachePeekMode.ALL);
    }

    public String getSessionId(String containerId) {
        String user = SecurityContextHolder.getContext().getAuthentication().getName();
        return sessionConfigurationProperties.getApplicationCacheVersion() + "_" + user + "_" + containerId;
    }
}
