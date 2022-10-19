package ee.openeid.siga.service.signature.container.asic;

import ee.openeid.siga.common.session.AsicContainerSession;
import ee.openeid.siga.service.signature.client.SivaClient;
import ee.openeid.siga.service.signature.session.AsicSessionHolder;
import ee.openeid.siga.session.SessionService;
import ee.openeid.siga.webapp.json.ValidationConclusion;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Service
@Profile("datafileContainer")
@RequiredArgsConstructor
public class AsicContainerValidationService implements AsicSessionHolder {
    private final SessionService sessionService;
    private final SivaClient sivaClient;

    public ValidationConclusion validateContainer(String containerName, String container) {
        return sivaClient.validateContainer(containerName, container);
    }

    public ValidationConclusion validateExistingContainer(String containerId) {
        AsicContainerSession sessionHolder = getSessionHolder(containerId);

        String container = new String(Base64.getEncoder().encode(sessionHolder.getContainer()));
        return sivaClient.validateContainer(sessionHolder.getContainerName(), container);
    }

    @Override
    public SessionService getSessionService() {
        return sessionService;
    }
}
