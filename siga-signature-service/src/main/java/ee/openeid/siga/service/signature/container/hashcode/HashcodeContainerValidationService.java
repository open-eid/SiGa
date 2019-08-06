package ee.openeid.siga.service.signature.container.hashcode;


import ee.openeid.siga.common.HashcodeDataFile;
import ee.openeid.siga.common.HashcodeSignatureWrapper;
import ee.openeid.siga.common.exception.InvalidContainerException;
import ee.openeid.siga.common.session.HashcodeContainerSessionHolder;
import ee.openeid.siga.service.signature.client.SivaClient;
import ee.openeid.siga.service.signature.hashcode.HashcodeContainer;
import ee.openeid.siga.service.signature.session.HashcodeSessionHolder;
import ee.openeid.siga.session.SessionService;
import ee.openeid.siga.webapp.json.ValidationConclusion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;

@Service
public class HashcodeContainerValidationService implements HashcodeSessionHolder {

    private SivaClient sivaClient;
    private SessionService sessionService;

    public ValidationConclusion validateContainer(String container) {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        hashcodeContainer.open(new ByteArrayInputStream(Base64.getDecoder().decode(container.getBytes())));
        validateContainerSignatures(hashcodeContainer.getSignatures());
        return createValidationConclusion(hashcodeContainer.getSignatures(), hashcodeContainer.getDataFiles());
    }

    public ValidationConclusion validateExistingContainer(String containerId) {
        HashcodeContainerSessionHolder sessionHolder = getSessionHolder(containerId);
        validateContainerSignatures(sessionHolder.getSignatures());
        return createValidationConclusion(sessionHolder.getSignatures(), sessionHolder.getDataFiles());
    }

    private void validateContainerSignatures(List<HashcodeSignatureWrapper> signatureWrappers) {
        if (signatureWrappers == null || signatureWrappers.isEmpty())
            throw new InvalidContainerException("Missing signatures");
    }

    private ValidationConclusion createValidationConclusion(List<HashcodeSignatureWrapper> signatureWrappers, List<HashcodeDataFile> dataFiles) {
        return sivaClient.validateHashcodeContainer(signatureWrappers, dataFiles);
    }

    @Autowired
    protected void setSivaClient(SivaClient sivaClient) {
        this.sivaClient = sivaClient;
    }

    @Override
    public SessionService getSessionService() {
        return sessionService;
    }

    @Autowired
    public void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }
}
