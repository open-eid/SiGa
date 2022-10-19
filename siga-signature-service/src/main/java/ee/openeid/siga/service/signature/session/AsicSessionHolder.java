package ee.openeid.siga.service.signature.session;

import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.common.session.AsicContainerSession;
import ee.openeid.siga.common.session.Session;

public interface AsicSessionHolder extends DataFileSessionHolder {

    default AsicContainerSession getSessionHolder(String containerId) {
        Session session = getContainerSession(containerId);
        if (session instanceof AsicContainerSession) {
            return (AsicContainerSession) session;
        }
        throw new TechnicalException("Unable to parse session object");
    }

}

