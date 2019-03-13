package ee.openeid.siga.service.signature;


import ee.openeid.siga.common.HashcodeDataFile;
import ee.openeid.siga.common.SignatureWrapper;
import ee.openeid.siga.common.session.DetachedDataFileContainerSessionHolder;
import ee.openeid.siga.service.signature.client.SivaClient;
import ee.openeid.siga.service.signature.hashcode.DetachedDataFileContainer;
import ee.openeid.siga.session.SessionService;
import ee.openeid.siga.webapp.json.ValidationConclusion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class DetachedDataFileContainerValidationService implements DetachedDataFileSessionHolder {

    private SivaClient sivaClient;
    private SessionService sessionService;

    public ValidationConclusion validateContainer(String container) {
        DetachedDataFileContainer detachedDataFileContainer = new DetachedDataFileContainer();
        detachedDataFileContainer.open(new ByteArrayInputStream(Base64.getDecoder().decode(container.getBytes())));
        return createValidationConclusion(detachedDataFileContainer.getSignatures(), detachedDataFileContainer.getDataFiles());
    }

    public ValidationConclusion validateExistingContainer(String containerId) {
        DetachedDataFileContainerSessionHolder sessionHolder = getSession(containerId);
        return createValidationConclusion(sessionHolder.getSignatures(), sessionHolder.getDataFiles());
    }

    private ValidationConclusion createValidationConclusion(List<SignatureWrapper> signatureWrappers, List<HashcodeDataFile> dataFiles) {
        List<ValidationConclusion> validationConclusions = new ArrayList<>();
        signatureWrappers.forEach(signatureWrapper ->
                validationConclusions.add(sivaClient.validateDetachedDataFileContainer(signatureWrapper, dataFiles)));
        return mergeValidationConclusions(validationConclusions);
    }

    private ValidationConclusion mergeValidationConclusions(List<ValidationConclusion> validationConclusions) {
        int signaturesCount = 0;
        int validSignaturesCount = 0;
        ValidationConclusion response = null;

        for (ValidationConclusion validationConclusion : validationConclusions) {
            if (signaturesCount == 0) {
                response = validationConclusion;
                validSignaturesCount = validationConclusion.getValidSignaturesCount();
            } else {
                response.getSignatures().addAll(validationConclusion.getSignatures());
                validSignaturesCount = validSignaturesCount + validationConclusion.getValidSignaturesCount();
            }
            signaturesCount = signaturesCount + validationConclusion.getSignaturesCount();
        }

        if (response != null) {
            response.setSignaturesCount(signaturesCount);
            response.setValidSignaturesCount(validSignaturesCount);
        }

        return response;
    }

    @Autowired
    protected void setSivaClient(SivaClient sivaClient) {
        this.sivaClient = sivaClient;
    }

    @Autowired
    protected void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public SessionService getSessionService() {
        return sessionService;
    }
}
