package ee.openeid.siga.service.signature;

import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.common.session.DetachedDataFileContainerSessionHolder;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.session.SessionService;

public interface DetachedDataFileSessionHolder {

    default DetachedDataFileContainerSessionHolder getSession(String containerId) {
        Session session = getSessionService().getContainer(containerId);
        if (session instanceof DetachedDataFileContainerSessionHolder) {
            return (DetachedDataFileContainerSessionHolder) session;
        }
        throw new TechnicalException("Unable to parse session response");
    }

    SessionService getSessionService();
}
