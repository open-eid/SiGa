package ee.openeid.siga.service.signature.session;

import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.common.session.Session;

public interface HashcodeSessionHolder extends DataFileSessionHolder {

    default HashcodeContainerSession getSessionHolder(String containerId) {
        Session session = getContainerSession(containerId);
        if (session instanceof HashcodeContainerSession) {
            return (HashcodeContainerSession) session;
        }
        throw new TechnicalException("Unable to parse session object");
    }

}
