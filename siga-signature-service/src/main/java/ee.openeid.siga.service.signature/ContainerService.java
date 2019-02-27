package ee.openeid.siga.service.signature;

import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.common.session.HashCodeContainerSessionHolder;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.session.SessionService;

public abstract class ContainerService {

    HashCodeContainerSessionHolder getSession(String containerId) {
        Session session = getSessionService().getContainer(containerId);
        if (session instanceof HashCodeContainerSessionHolder) {
            return (HashCodeContainerSessionHolder) session;
        }
        throw new TechnicalException("Unable to parse session");
    }

    abstract SessionService getSessionService();
}
