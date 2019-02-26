package ee.openeid.siga.service.signature;


import ee.openeid.siga.service.signature.client.SivaClient;
import ee.openeid.siga.service.signature.hashcode.HashCodeContainer;
import ee.openeid.siga.webapp.json.CreateHashCodeValidationReportRequest;
import ee.openeid.siga.webapp.json.CreateHashCodeValidationReportResponse;
import ee.openeid.siga.webapp.json.ValidationConclusion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class ValidationServiceImpl implements ValidationService {

    private SivaClient sivaClient;

    @Override
    public CreateHashCodeValidationReportResponse validateContainer(CreateHashCodeValidationReportRequest request) {
        HashCodeContainer hashCodeContainer = new HashCodeContainer();
        hashCodeContainer.open(new ByteArrayInputStream(Base64.getDecoder().decode(request.getContainer().getBytes())));
        ValidationConclusion validationConclusion = createValidationConclusion(hashCodeContainer);
        CreateHashCodeValidationReportResponse response = new CreateHashCodeValidationReportResponse();
        response.setValidationConclusion(validationConclusion);
        return response;
    }

    private ValidationConclusion createValidationConclusion(HashCodeContainer hashCodeContainer) {
        List<ValidationConclusion> validationConclusions = new ArrayList<>();
        hashCodeContainer.getSignatures().forEach(signatureWrapper ->
                validationConclusions.add(sivaClient.validateHashCodeContainer(signatureWrapper)));
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
    public void setSivaClient(SivaClient sivaClient) {
        this.sivaClient = sivaClient;
    }
}
