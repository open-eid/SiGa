package ee.openeid.siga.service.signature.container.status;

import ee.openeid.siga.common.session.AsicContainerSession;
import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.service.signature.configuration.SessionStatusReprocessingProperties;
import ee.openeid.siga.service.signature.container.ContainerSigningService;
import ee.openeid.siga.service.signature.container.asic.AsicContainerSigningService;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerSigningService;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(SessionStatusReprocessingProperties.class)
class ContainerSigningServiceSelector {

    private final HashcodeContainerSigningService hashcodeContainerSigningService;
    private final AsicContainerSigningService asicContainerSigningService;

    public ContainerSigningServiceSelector(
            @Autowired @NonNull HashcodeContainerSigningService hashcodeContainerSigningService,
            @Autowired(required = false) AsicContainerSigningService asicContainerSigningService
    ) {
        this.hashcodeContainerSigningService = hashcodeContainerSigningService;
        this.asicContainerSigningService = asicContainerSigningService;
    }

    public ContainerSigningService getContainerSigningServiceFor(Session session) {
        if (session instanceof HashcodeContainerSession) {
            return hashcodeContainerSigningService;
        } else if (session instanceof AsicContainerSession) {
            return asicContainerSigningService;
        } else {
            return null;
        }
    }

}
