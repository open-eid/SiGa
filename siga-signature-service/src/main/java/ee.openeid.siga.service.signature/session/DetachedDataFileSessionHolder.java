package ee.openeid.siga.service.signature.session;

import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.common.session.DetachedDataFileContainerSessionHolder;
import ee.openeid.siga.common.session.Session;

public interface DetachedDataFileSessionHolder extends DataFileSessionHolder {

    default DetachedDataFileContainerSessionHolder getSessionHolder(String containerId) {
        Session session = getContainerSession(containerId);
        if (session instanceof DetachedDataFileContainerSessionHolder) {
            return (DetachedDataFileContainerSessionHolder) session;
        }
        throw new TechnicalException("Unable to parse session object");
    }

}
