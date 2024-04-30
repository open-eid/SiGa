package ee.openeid.siga.service.signature.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.openeid.siga.common.client.HttpPostClient;
import ee.openeid.siga.common.client.HttpStatusException;
import ee.openeid.siga.common.exception.ClientException;
import ee.openeid.siga.common.exception.InvalidContainerException;
import ee.openeid.siga.common.exception.InvalidHashAlgorithmException;
import ee.openeid.siga.common.exception.InvalidSignatureException;
import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.common.model.HashcodeDataFile;
import ee.openeid.siga.common.model.HashcodeSignatureWrapper;
import ee.openeid.siga.common.model.SignatureHashcodeDataFile;
import ee.openeid.siga.webapp.json.ValidationConclusion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.SignatureProfile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SivaClient {
    private static final String HASHCODE_VALIDATION_ENDPOINT = "/validateHashcode";
    private static final String VALIDATION_ENDPOINT = "/validate";
    private static final String DOCUMENT_KEY = "document";
    private static final String SIGNATURE_KEY = "signatureFiles.signature";
    private final HttpPostClient sivaHttpClient;

    public ValidationConclusion validateHashcodeContainer(List<HashcodeSignatureWrapper> signatureWrappers, List<HashcodeDataFile> dataFiles) {
        SivaHashcodeValidationRequest request = createHashcodeRequest(signatureWrappers, dataFiles);
        ValidationConclusion validationResponse = validate(request, HASHCODE_VALIDATION_ENDPOINT);
        validateLTASignatureProfile(validationResponse);
        return validationResponse;
    }

    public ValidationConclusion validateContainer(String name, String container) {
        SivaValidationRequest request = new SivaValidationRequest();
        request.setFilename(name);
        request.setDocument(container);
        return validate(request, VALIDATION_ENDPOINT);
    }

    private void handleHttpStatusCodeException(HttpStatusException e) {
        HttpStatus httpStatus = e.getHttpStatus();
        if (HttpStatus.BAD_REQUEST == httpStatus) {
            SivaRequestValidationError errorResponse = parseErrorResponse(e);
            for (SivaErrorResponse error : errorResponse.getRequestErrors()) {
                if (DOCUMENT_KEY.equals(error.getKey())) {
                    throw new InvalidContainerException("Document malformed");
                } else if (SIGNATURE_KEY.equals(error.getKey())) {
                    throw new InvalidSignatureException("Signature malformed");
                }
            }
        }
        if (httpStatus == null) {
            log.error("Unexpected exception was thrown by SiVa. Status: unknown, Response body: {} ", tryToParseResponseBody(e.getResponseBody()));
        } else {
            log.error("Unexpected exception was thrown by SiVa. Status: {}-{}, Response body: {} ", httpStatus.value(), httpStatus.getReasonPhrase(), tryToParseResponseBody(e.getResponseBody()));
        }
        throw new TechnicalException("Unable to get valid response from client");
    }

    private static String tryToParseResponseBody(byte[] responseBody) {
        if (responseBody == null) {
            return null;
        }
        try {
            return new String(responseBody, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return Base64.getEncoder().encodeToString(responseBody);
        }
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
        ValidationResponse validationResponse;
        try {
            validationResponse = sivaHttpClient.post(validationEndpoint, request, ValidationResponse.class);
        } catch (HttpStatusException e) {
            handleHttpStatusCodeException(e);
            throw e; // cannot reach here
        } catch (Exception e) {
            throw new TechnicalException("SIVA service error", e);
        }

        ValidationConclusion validationConclusion = Optional
                .ofNullable(validationResponse)
                .map(ValidationResponse::getValidationReport)
                .map(ValidationReport::getValidationConclusion)
                .orElseThrow(() -> new TechnicalException("Unable to parse client empty response"));
        log.info("Container validation details received successfully");

        return validationConclusion;
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
        String hashAlgorithm = getHashAlgorithm(signatureDataFiles, dataFile.getFileName());
        if (hashAlgorithm == null) {
            // There are apparently some malformed containers out there that have encoded "+" character in filename as
            // literal "+" instead of "%2B" in URI field in signatures file. However, "+" is decoded as space (" "), so
            // we wouldn't find a match between signatureDataFiles and dataFile in these cases. To work around this, we
            // try to match the wrongly decoded filenames in case the correct decoding gives no match.
            hashAlgorithm = getHashAlgorithm(signatureDataFiles, dataFile.getFileName().replaceAll("\\+", " "));
        }
        if (hashAlgorithm == null) {
            throw new InvalidHashAlgorithmException("Container contains invalid hash algorithms");
        }
        return hashAlgorithm;
    }

    private static String getHashAlgorithm(List<SignatureHashcodeDataFile> signatureDataFiles, String dataFileName) {
        for (SignatureHashcodeDataFile signatureDataFile : signatureDataFiles) {
            if (signatureDataFile.getFileName().equals(dataFileName)) {
                String hashAlgorithm = signatureDataFile.getHashAlgo();
                if (DigestAlgorithm.SHA256.name().equals(hashAlgorithm) || DigestAlgorithm.SHA512.name().equals(hashAlgorithm)) {
                    return hashAlgorithm;
                }
            }
        }
        return null;
    }

    private SivaRequestValidationError parseErrorResponse(HttpStatusException e) {
        try {
            return new ObjectMapper().readValue(e.getResponseBody(), SivaRequestValidationError.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not parse SiVa error response body. Is this a valid JSON in the expected format?", ex);
        }
    }
}
