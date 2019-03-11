package ee.openeid.siga.session;

import ee.openeid.siga.common.exception.ResourceNotFoundException;
import ee.openeid.siga.common.session.Session;
import org.apache.ignite.Ignite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.cache.Cache;
import java.util.Optional;

@Component
public class SessionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionService.class);

    Ignite ignite;

    public Session getContainer(String containerId) {
        String sessionId = getSessionId(containerId);
        Session container = Optional.ofNullable(getContainerConfigCache().get(sessionId))
                .orElseThrow(() -> new ResourceNotFoundException("Session [" + containerId + "] not found"));
        LOGGER.info("Found container with container ID [{}]", containerId);
        return container;
    }

    public void update(String containerId, Session session) {
        String sessionId = getSessionId(containerId);
        getContainerConfigCache().put(sessionId, session);
    }

    public void remove(String containerId) {
        String sessionId = getSessionId(containerId);
        getContainerConfigCache().remove(sessionId);
    }

    private Cache<String, Session> getContainerConfigCache() {
        return ignite.getOrCreateCache(CacheName.CONTAINER.name());
    }

    private String getSessionId(String containerId) {
        String user = SecurityContextHolder.getContext().getAuthentication().getName();
        return user + "_" + containerId;
    }

    @Autowired
    protected void setIgnite(Ignite ignite) {
        this.ignite = ignite;
    }
}
