package ee.openeid.siga.session;

import ee.openeid.siga.common.ContainerWrapper;

public interface SessionService {

    ContainerWrapper getContainer(String sessionId);

    void update(String sessionId, ContainerWrapper container);

}
