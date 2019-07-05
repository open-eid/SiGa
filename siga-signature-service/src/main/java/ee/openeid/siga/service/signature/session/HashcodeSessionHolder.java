package ee.openeid.siga.service.signature.session;

import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.common.session.HashcodeContainerSessionHolder;
import ee.openeid.siga.common.session.Session;

public interface HashcodeSessionHolder extends DataFileSessionHolder {

    default HashcodeContainerSessionHolder getSessionHolder(String containerId) {
        Session session = getContainerSession(containerId);
        if (session instanceof HashcodeContainerSessionHolder) {
            return (HashcodeContainerSessionHolder) session;
        }
        throw new TechnicalException("Unable to parse session object");
    }

}
