package ee.openeid.siga.service.signature.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.openeid.siga.common.model.HashcodeDataFile;
import ee.openeid.siga.common.model.HashcodeSignatureWrapper;
import ee.openeid.siga.common.model.SignatureHashcodeDataFile;
import ee.openeid.siga.common.exception.ClientException;
import ee.openeid.siga.common.exception.InvalidContainerException;
import ee.openeid.siga.common.exception.InvalidHashAlgorithmException;
import ee.openeid.siga.common.exception.InvalidSignatureException;
import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.service.signature.configuration.SivaClientConfigurationProperties;
import ee.openeid.siga.webapp.json.ValidationConclusion;
import lombok.extern.slf4j.Slf4j;
import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.SignatureProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Component
public class SivaClient {
    private static final String HASHCODE_VALIDATION_ENDPOINT = "/validateHashcode";
    private static final String VALIDATION_ENDPOINT = "/validate";
    private static final String DOCUMENT_KEY = "document";
    private static final String SIGNATURE_KEY = "signatureFiles.signature";

    private RestTemplate restTemplate;
    private SivaClientConfigurationProperties configurationProperties;

    public ValidationConclusion validateHashcodeContainer(List<HashcodeSignatureWrapper> signatureWrappers, List<HashcodeDataFile> dataFiles) {
        SivaHashcodeValidationRequest request = createHashcodeRequest(signatureWrappers, dataFiles);
        try {
            ValidationConclusion validationResponse = validate(request, HASHCODE_VALIDATION_ENDPOINT);
            validateLTASignatureProfile(validationResponse);
            return validationResponse;
        } catch (HttpStatusCodeException e) {
            handleHttpStatusCodeException(e);
            throw e; //Cannot reach here
        }
    }

    public ValidationConclusion validateContainer(String name, String container) {
        SivaValidationRequest request = new SivaValidationRequest();
        request.setFilename(name);
        request.setDocument(container);
        try {
            return validate(request, VALIDATION_ENDPOINT);
        } catch (HttpStatusCodeException e) {
            handleHttpStatusCodeException(e);
            throw e; //Cannot reach here
        }
    }

    private void handleHttpStatusCodeException(HttpStatusCodeException e) {
        if (HttpStatus.BAD_REQUEST == e.getStatusCode()) {
            SivaRequestValidationError errorResponse = parseErrorResponse(e);
            for (SivaErrorResponse error : errorResponse.getRequestErrors()) {
                if (DOCUMENT_KEY.equals(error.getKey())) {
                    throw new InvalidContainerException("Document malformed");
                } else if (SIGNATURE_KEY.equals(error.getKey())) {
                    throw new InvalidSignatureException("Signature malformed");
                }
            }
        }
        log.error("Unexpected exception was thrown by SiVa. Status: {}-{}, Response body: {} ", e.getRawStatusCode(), e.getStatusText(), e.getResponseBodyAsString());
        throw new TechnicalException("Unable to get valid response from client");
    }


    private void validateLTASignatureProfile(ValidationConclusion validationConclusion) {
        validationConclusion.getSignatures().forEach(
                signature -> {
                    if (("XAdES_BASELINE_" + SignatureProfile.LTA.name()).equals(signature.getSignatureFormat())) {
                        throw new ClientException("Unable to validate container! Container contains signature with unsupported signature profile: LTA");
                    }
                });
    }

    private ValidationConclusion validate(Object request, String validationEndpoint) {
        ResponseEntity<ValidationResponse> responseEntity;
        responseEntity = restTemplate.exchange(configurationProperties.getUrl() + validationEndpoint,
                HttpMethod.POST, formHttpEntity(request), ValidationResponse.class);
        if (responseEntity.getBody() == null) {
            throw new TechnicalException("Unable to parse client empty response");
        }
        log.info("Container validation details received successfully");
        return responseEntity.getBody().getValidationReport().getValidationConclusion();
    }

    private SivaHashcodeValidationRequest createHashcodeRequest(List<HashcodeSignatureWrapper>
                                                                        signatureWrappers, List<HashcodeDataFile> dataFiles) {
        SivaHashcodeValidationRequest request = new SivaHashcodeValidationRequest();
        signatureWrappers.forEach(signatureWrapper -> {
            SignatureFile signatureFile = new SignatureFile();
            signatureFile.setSignature(new String(Base64.getEncoder().encode(signatureWrapper.getSignature())));

            List<SivaDataFile> sivaDataFiles = new ArrayList<>();
            dataFiles.forEach(dataFile -> {
                String hash;
                String hashAlgorithm = getDataFileHashAlgorithm(signatureWrapper.getDataFiles(), dataFile);
                if (DigestAlgorithm.SHA256.name().equals(hashAlgorithm)) {
                    hash = dataFile.getFileHashSha256();
                } else {
                    hash = dataFile.getFileHashSha512();
                }
                SivaDataFile sivaDataFile = new SivaDataFile();
                sivaDataFile.setFilename(dataFile.getFileName());
                sivaDataFile.setHash(hash);
                sivaDataFile.setHashAlgo(hashAlgorithm);
                sivaDataFiles.add(sivaDataFile);
            });
            signatureFile.setDatafiles(sivaDataFiles);
            request.getSignatureFiles().add(signatureFile);
        });
        return request;
    }

    private String getDataFileHashAlgorithm(List<SignatureHashcodeDataFile> signatureDataFiles, HashcodeDataFile
            dataFile) {
        for (SignatureHashcodeDataFile signatureDataFile : signatureDataFiles) {
            if (signatureDataFile.getFileName().equals(dataFile.getFileName())) {
                String hashAlgorithm = signatureDataFile.getHashAlgo();
                if (DigestAlgorithm.SHA256.name().equals(hashAlgorithm) || DigestAlgorithm.SHA512.name().equals(hashAlgorithm)) {
                    return hashAlgorithm;
                }
            }
        }
        throw new InvalidHashAlgorithmException("Container contains invalid hash algorithms");
    }

    private SivaRequestValidationError parseErrorResponse(HttpStatusCodeException e) {
        try {
            return new ObjectMapper().readValue(e.getResponseBodyAsString(), SivaRequestValidationError.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not parse SiVa error response body. Is this a valid JSON in the expected format?", ex);
        }
    }

    private HttpEntity<?> formHttpEntity(Object object) {
        return new HttpEntity<>(object);
    }

    @Autowired
    protected void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Autowired
    protected void setConfigurationProperties(SivaClientConfigurationProperties configurationProperties) {
        this.configurationProperties = configurationProperties;
    }

}
