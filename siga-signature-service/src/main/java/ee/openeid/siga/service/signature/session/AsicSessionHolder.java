package ee.openeid.siga.service.signature.session;

import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.common.session.AsicContainerSessionHolder;
import ee.openeid.siga.common.session.Session;

public interface AsicSessionHolder extends DataFileSessionHolder {

    default AsicContainerSessionHolder getSessionHolder(String containerId) {
        Session session = getContainerSession(containerId);
        if (session instanceof AsicContainerSessionHolder) {
            return (AsicContainerSessionHolder) session;
        }
        throw new TechnicalException("Unable to parse session object");
    }

}

