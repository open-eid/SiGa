package ee.openeid.siga.service.signature.container.asic;


import ee.openeid.siga.common.session.AsicContainerSessionHolder;
import ee.openeid.siga.service.signature.client.SivaClient;
import ee.openeid.siga.service.signature.session.AsicSessionHolder;
import ee.openeid.siga.session.SessionService;
import ee.openeid.siga.webapp.json.ValidationConclusion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Service
public class AsicContainerValidationService implements AsicSessionHolder {

    private SessionService sessionService;
    private SivaClient sivaClient;

    public ValidationConclusion validateContainer(String containerName, String container) {
        return sivaClient.validateAsicContainer(containerName, container);
    }

    public ValidationConclusion validateExistingContainer(String containerId) {
        AsicContainerSessionHolder sessionHolder = getSessionHolder(containerId);

        String container = new String(Base64.getEncoder().encode(sessionHolder.getContainer()));
        return sivaClient.validateAsicContainer(sessionHolder.getContainerName(), container);
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
