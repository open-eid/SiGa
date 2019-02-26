package ee.openeid.siga.service.signature.client;

import ee.openeid.siga.common.SignatureWrapper;
import ee.openeid.siga.service.signature.configuration.SivaConfigurationProperties;
import ee.openeid.siga.webapp.json.ValidationConclusion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
public class SivaClient {

    private static final String SIGNATURE_FILE_NAME = "signatures0.xml";
    private static final String VALIDATION_ENDPOINT = "/validateHashcode";
    private RestTemplate restTemplate;
    private SivaConfigurationProperties configurationProperties;


    public ValidationConclusion validateHashCodeContainer(SignatureWrapper signatureWrapper) {
        SivaValidationRequest request = createRequest(signatureWrapper);
        ResponseEntity<ValidationResponse> responseEntity = restTemplate.exchange(configurationProperties.getUrl() + VALIDATION_ENDPOINT,
                HttpMethod.POST, formHttpEntity(request), ValidationResponse.class);
        return responseEntity.getBody().getValidationReport().getValidationConclusion();
    }

    private SivaValidationRequest createRequest(SignatureWrapper signatureWrapper) {
        SivaValidationRequest request = new SivaValidationRequest();
        request.setFilename(SIGNATURE_FILE_NAME);
        request.setSignatureFile(new String(Base64.getEncoder().encode(signatureWrapper.getSignature().getAdESSignature())));

        List<SivaDataFile> sivaDataFiles = new ArrayList<>();
        signatureWrapper.getDataFiles().forEach(dataFile -> {
            SivaDataFile sivaDataFile = new SivaDataFile();
            sivaDataFile.setFilename(dataFile.getFileName());
            sivaDataFile.setHash(dataFile.getHash());
            sivaDataFile.setHashAlgo(dataFile.getHashAlgo());
            sivaDataFiles.add(sivaDataFile);
        });
        request.setDatafiles(sivaDataFiles);
        return request;
    }

    private HttpEntity<?> formHttpEntity(Object object) {
        return new HttpEntity<>(object);
    }

    @Autowired
    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Autowired
    public void setConfigurationProperties(SivaConfigurationProperties configurationProperties) {
        this.configurationProperties = configurationProperties;
    }
}
