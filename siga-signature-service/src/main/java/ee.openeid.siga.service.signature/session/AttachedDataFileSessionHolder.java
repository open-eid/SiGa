package ee.openeid.siga.service.signature.session;

import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.common.session.AttachedDataFileContainerSessionHolder;
import ee.openeid.siga.common.session.Session;

public interface AttachedDataFileSessionHolder extends DataFileSessionHolder {

    default AttachedDataFileContainerSessionHolder getSessionHolder(String containerId) {
        Session session = getContainerSession(containerId);
        if (session instanceof AttachedDataFileContainerSessionHolder) {
            return (AttachedDataFileContainerSessionHolder) session;
        }
        throw new TechnicalException("Unable to parse session object");
    }

}

