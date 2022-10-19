package ee.openeid.siga.service.signature.container.hashcode;


import ee.openeid.siga.common.exception.InvalidContainerException;
import ee.openeid.siga.common.model.HashcodeDataFile;
import ee.openeid.siga.common.model.HashcodeSignatureWrapper;
import ee.openeid.siga.common.model.ServiceType;
import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.service.signature.client.SivaClient;
import ee.openeid.siga.service.signature.hashcode.HashcodeContainer;
import ee.openeid.siga.service.signature.session.HashcodeSessionHolder;
import ee.openeid.siga.session.SessionService;
import ee.openeid.siga.webapp.json.SignatureScope;
import ee.openeid.siga.webapp.json.ValidationConclusion;
import ee.openeid.siga.webapp.json.Warning;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
public class HashcodeContainerValidationService implements HashcodeSessionHolder {

    private static final String XML_BEGINNING_TAG = "<?xml";
    private static final String HASHCODE_CONTENT_TYPE = "ContentType=\"HASHCODE\"";
    private static final String DDOC_CONTAINER_NAME = "container.ddoc";
    private final SivaClient sivaClient;
    private final SessionService sessionService;

    public ValidationConclusion validateContainer(String container, ServiceType serviceType) {
        byte[] decodedContainer = Base64.getDecoder().decode(container.getBytes());
        if (isHashcodeDDOC(decodedContainer)) {
            return validateDDOCHashcodeContainer(decodedContainer);
        }
        return validateHashcodeContainer(decodedContainer, serviceType);
    }

    private ValidationConclusion validateDDOCHashcodeContainer(byte[] container) {
        String encodedContainer = new String(Base64.getEncoder().encode(container));
        return createDDOCHashcodeContainerValidationConclusion(encodedContainer);
    }

    private ValidationConclusion validateHashcodeContainer(byte[] container, ServiceType serviceType) {
        HashcodeContainer hashcodeContainer = new HashcodeContainer(serviceType);
        hashcodeContainer.open(container);
        validateContainerSignatures(hashcodeContainer.getSignatures());
        return createHashcodeContainerValidationConclusion(hashcodeContainer.getSignatures(), hashcodeContainer.getDataFiles());
    }

    public ValidationConclusion validateExistingContainer(String containerId) {
        HashcodeContainerSession sessionHolder = getSessionHolder(containerId);
        validateContainerSignatures(sessionHolder.getSignatures());
        return createHashcodeContainerValidationConclusion(sessionHolder.getSignatures(), sessionHolder.getDataFiles());
    }

    private void validateContainerSignatures(List<HashcodeSignatureWrapper> signatureWrappers) {
        if (signatureWrappers == null || signatureWrappers.isEmpty())
            throw new InvalidContainerException("Missing signatures");
    }

    private boolean isHashcodeDDOC(byte[] container) {
        String content = new String(container);
        if (content.startsWith(XML_BEGINNING_TAG)) {
            if (content.contains(HASHCODE_CONTENT_TYPE)) {
                return true;
            }
            throw new InvalidContainerException("EMBEDDED DDOC is not supported");
        }
        return false;
    }

    private ValidationConclusion createHashcodeContainerValidationConclusion(List<HashcodeSignatureWrapper> signatureWrappers, List<HashcodeDataFile> dataFiles) {
        ValidationConclusion validationConclusion = sivaClient.validateHashcodeContainer(signatureWrappers, dataFiles);
        validateHashcodeContainerDataFileSizes(validationConclusion, dataFiles);
        return validationConclusion;
    }

    private static void validateHashcodeContainerDataFileSizes(final ValidationConclusion validationConclusion, List<HashcodeDataFile> hashcodeDataFiles) {
        hashcodeDataFiles.forEach(hashcodeDataFile -> {
            if (hashcodeDataFile.getFileSize() == null || hashcodeDataFile.getFileSize() > 0) {
                return;
            }

            final String fileName = hashcodeDataFile.getFileName();
            final Predicate<SignatureScope> dataFileMatcher = signatureScope -> StringUtils.equals(signatureScope.getName(), fileName);

            validationConclusion.getSignatures().forEach(signature -> {
                if (signature.getSignatureScopes().stream().noneMatch(dataFileMatcher)) {
                    return;
                }
                Warning warning = new Warning();
                warning.setContent(String.format("Data file '%s' is empty", fileName));
                signature.getWarnings().add(warning);
            });
        });
    }

    private ValidationConclusion createDDOCHashcodeContainerValidationConclusion(String container) {
        return sivaClient.validateContainer(DDOC_CONTAINER_NAME, container);
    }

    @Override
    public SessionService getSessionService() {
        return sessionService;
    }
}
