package ee.openeid.siga.service.signature.session;

import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.session.SessionService;

public interface DataFileSessionHolder {

    default Session getSession(String containerId) {
        return getSessionService().getContainer(containerId);
    }

    SessionService getSessionService();
}
