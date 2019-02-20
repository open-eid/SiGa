package ee.openeid.siga.session;

import ee.openeid.siga.common.ContainerWrapper;
import ee.openeid.siga.session.exception.ResourceNotFoundException;
import org.apache.ignite.Ignite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.cache.Cache;
import java.util.Optional;

@Component
public class IgniteSessionService implements SessionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IgniteSessionService.class);

    @Autowired
    private Ignite ignite;

    @Override
    public ContainerWrapper getContainer(String sessionId) {
        ContainerWrapper container = Optional.ofNullable(getContainerConfigCache().get(sessionId))
                .orElseThrow(() -> new ResourceNotFoundException("Session [" + sessionId + "] not found"));
        LOGGER.info("Found container with session ID [{}]", sessionId);
        return container;
    }

    @Override
    public void update(String sessionId, ContainerWrapper container) {
        getContainerConfigCache().put(sessionId, container);
    }

    public Cache<String, ContainerWrapper> getContainerConfigCache() {
        return ignite.getOrCreateCache(CacheName.CONTAINER.name());
    }


}
