package ee.openeid.siga.service.signature.container.attached;


import ee.openeid.siga.common.session.AttachedDataFileContainerSessionHolder;
import ee.openeid.siga.service.signature.client.SivaClient;
import ee.openeid.siga.service.signature.session.AttachedDataFileSessionHolder;
import ee.openeid.siga.session.SessionService;
import ee.openeid.siga.webapp.json.ValidationConclusion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Service
public class AttachedDataFileContainerValidationService implements AttachedDataFileSessionHolder {

    private SessionService sessionService;
    private SivaClient sivaClient;

    public ValidationConclusion validateContainer(String containerName, String container) {
        return sivaClient.validateAttachedDataFileContainer(containerName, container);
    }

    public ValidationConclusion validateExistingContainer(String containerId) {
        AttachedDataFileContainerSessionHolder sessionHolder = getSessionHolder(containerId);

        String container = new String(Base64.getEncoder().encode(sessionHolder.getContainer()));
        return sivaClient.validateAttachedDataFileContainer(sessionHolder.getContainerName(), container);
    }

    @Override
    public SessionService getSessionService() {
        return sessionService;
    }


    @Autowired
    protected void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Autowired
    public void setSivaClient(SivaClient sivaClient) {
        this.sivaClient = sivaClient;
    }
}
