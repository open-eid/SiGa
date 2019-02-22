package ee.openeid.siga.session;

import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.session.exception.ResourceNotFoundException;
import org.apache.ignite.Ignite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.cache.Cache;
import java.util.Optional;

public abstract class SessionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(HashCodeSessionService.class);

    @Autowired
    Ignite ignite;

    public Session getContainer(String sessionId) {
        Session container = Optional.ofNullable(getContainerConfigCache().get(sessionId))
                .orElseThrow(() -> new ResourceNotFoundException("Session [" + sessionId + "] not found"));
        LOGGER.info("Found container with session ID [{}]", sessionId);
        return container;
    }

    public void update(String sessionId, Session session) {
        getContainerConfigCache().put(sessionId, session);
    }

    public abstract Cache<String, Session> getContainerConfigCache();
}
