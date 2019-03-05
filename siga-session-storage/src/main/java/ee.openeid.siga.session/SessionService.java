package ee.openeid.siga.session;

import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.exception.ResourceNotFoundException;
import org.apache.ignite.Ignite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.cache.Cache;
import java.util.Optional;

@Component
public class SessionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionService.class);

    Ignite ignite;

    public Session getContainer(String containerId) {
        Session container = Optional.ofNullable(getContainerConfigCache().get(containerId))
                .orElseThrow(() -> new ResourceNotFoundException("Session [" + containerId + "] not found"));
        LOGGER.info("Found container with container ID [{}]", containerId);
        return container;
    }

    public void update(String containerId, Session session) {
        getContainerConfigCache().put(containerId, session);
    }

    public void remove(String containerId) {
        getContainerConfigCache().remove(containerId);
    }

    private Cache<String, Session> getContainerConfigCache() {
        return ignite.getOrCreateCache(CacheName.CONTAINER.name());
    }

    @Autowired
    protected void setIgnite(Ignite ignite) {
        this.ignite = ignite;
    }
}
