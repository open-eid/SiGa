package ee.openeid.siga.service.signature.container.asic;

import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.service.signature.session.AsicSessionHolder;
import ee.openeid.siga.service.signature.util.ContainerUtil;
import ee.openeid.siga.session.SessionService;
import eu.europa.esig.dss.enumerations.SignatureQualification;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerValidationResult;
import org.digidoc4j.Signature;
import org.digidoc4j.SignatureProfile;
import org.digidoc4j.X509Cert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Profile("datafileContainer")
public class AugmentationValidationService implements AsicSessionHolder {
    private static final List<SignatureProfile> augmentableSignatureProfiles = List.of(
            SignatureProfile.LT,
            SignatureProfile.LTA
    );
    private final SessionService sessionService;
    private final Configuration euConfiguration;

    @Autowired
    public AugmentationValidationService(
            SessionService sessionService,
            @Qualifier("euConfiguration") Configuration euConfiguration) {
        this.sessionService = sessionService;
        this.euConfiguration = euConfiguration;
    }

    public List<Signature> validateAndGetAugmentableSignatures(String containerId, Container container) {
        Container euContainer = ContainerUtil.createContainer(getSessionHolder(containerId).getContainer(), euConfiguration);
        validateNotEmpty(euContainer);
        List<org.digidoc4j.Signature> estonianSignatures = findEstonianSignaturesOrFail(euContainer);
        ContainerValidationResult euValidationResult = euContainer.validate();
        List<org.digidoc4j.Signature> signaturesWithoutESeals = findPersonalSignaturesOrFail(euValidationResult, estonianSignatures);
        validateSignatureProfiles(signaturesWithoutESeals);
        List<String> augmentableSignatureIds = signaturesWithoutESeals.stream()
                .map(Signature::getUniqueId)
                .toList();
        return getSignaturesByUniqueId(container, augmentableSignatureIds);
    }

    private List<Signature> getSignaturesByUniqueId(Container container, List<String> augmentableSignatureIds) {
        return container.getSignatures().stream()
                .filter(signature -> augmentableSignatureIds.contains(signature.getUniqueId()))
                .toList();
    }

    private List<org.digidoc4j.Signature> findEstonianSignaturesOrFail(Container container) {
        List<org.digidoc4j.Signature> estonianSignatures = container.getSignatures().stream()
                .filter(signature -> "EE".equals(signature.getSigningCertificate().getSubjectName(X509Cert.SubjectName.C)))
                .toList();
        if (estonianSignatures.isEmpty()) {
            throw new InvalidSessionDataException("Unable to augment. Container does not contain any Estonian signatures");
        }
        return estonianSignatures;
    }

    private List<org.digidoc4j.Signature> findPersonalSignaturesOrFail(ContainerValidationResult validationResult, List<org.digidoc4j.Signature> signatures) {
        List<org.digidoc4j.Signature> personalSignatures = new ArrayList<>(signatures.size());
        for (org.digidoc4j.Signature signature: signatures) {
            SignatureQualification signatureQualification = validationResult.getSignatureQualification(signature.getUniqueId());
            if (!signatureQualification.getReadable().contains("Seal")) {
                personalSignatures.add(signature);
            }
        }
        if (personalSignatures.isEmpty()) {
            throw new InvalidSessionDataException("Unable to augment. The only Estonian signatures in the container are e-Seals");
        }
        return personalSignatures;
    }

    private void validateNotEmpty(Container container) {
        if (container.getSignatures().isEmpty()) {
            throw new InvalidSessionDataException("Unable to augment. Container does not contain any signatures");
        }
    }

    private void validateSignatureProfiles(List<org.digidoc4j.Signature> signatures) {
        for (org.digidoc4j.Signature signature: signatures) {
            if (!augmentableSignatureProfiles.contains(signature.getProfile())) {
                throw new InvalidSessionDataException("Cannot augment signature profile " + signature.getProfile());
            }
        }
    }

    @Override
    public SessionService getSessionService() {
        return sessionService;
    }
}
