package ee.openeid.siga.session;

import ee.openeid.siga.common.session.Session;
import org.springframework.stereotype.Component;

import javax.cache.Cache;

@Component
public class DataFileSessionService extends SessionService {

    public Cache<String, Session> getContainerConfigCache() {
        return ignite.getOrCreateCache(CacheName.CONTAINER_SESSION.name());
    }
}
