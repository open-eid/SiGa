package ee.openeid.siga.session;

import ee.openeid.siga.common.exception.ResourceNotFoundException;
import ee.openeid.siga.common.session.Session;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.cache.Cache;
import java.util.Optional;

@Slf4j
@Component
public class SessionService {
    Ignite ignite;

    public Session getContainer(String containerId) {
        String sessionId = getSessionId(containerId);
        Session container = Optional.ofNullable(getContainerConfigCache().get(sessionId))
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        log.info("Found container with container ID [{}]", containerId);
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
