package ee.openeid.siga.service.signature.container.asic;

import ee.openeid.siga.common.event.SigaEvent;
import ee.openeid.siga.common.event.SigaEventLogger;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.service.signature.util.ContainerUtil;
import eu.europa.esig.dss.alert.exception.AlertException;
import eu.europa.esig.dss.enumerations.Indication;
import eu.europa.esig.dss.enumerations.MimeTypeEnum;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignatureQualification;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.xades.validation.XAdESSignature;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerValidationResult;
import org.digidoc4j.ServiceType;
import org.digidoc4j.Signature;
import org.digidoc4j.SignatureProfile;
import org.digidoc4j.Timestamp;
import org.digidoc4j.TimestampBuilder;
import org.digidoc4j.X509Cert;
import org.digidoc4j.impl.ServiceAccessListener;
import org.digidoc4j.impl.ServiceAccessScope;
import org.digidoc4j.impl.asic.AsicSignature;
import org.digidoc4j.impl.asic.DetachedContentCreator;
import org.digidoc4j.impl.asic.asics.AsicSContainerBuilder;
import org.digidoc4j.impl.asic.report.SignatureValidationReport;
import org.digidoc4j.impl.asic.xades.XadesValidationDssFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static ee.openeid.siga.common.event.SigaEventName.EventParam.REQUEST_URL;

@Slf4j
@Service
@Profile("datafileContainer")
public class AsiceContainerAugmentationService {
    private static final List<SignatureLevel> augmentableSignatureProfiles = List.of(
            SignatureLevel.XAdES_BASELINE_LT,
            SignatureLevel.XAdES_BASELINE_LTA
    );
    private final Configuration eeConfiguration;
    private final Configuration euConfiguration;
    private final SigaEventLogger sigaEventLogger;

    @Autowired
    public AsiceContainerAugmentationService(
            @Qualifier("configuration") Configuration eeConfiguration,
            @Qualifier("euConfiguration") Configuration euConfiguration,
            SigaEventLogger sigaEventLogger) {
        this.eeConfiguration = eeConfiguration;
        this.euConfiguration = euConfiguration;
        this.sigaEventLogger = sigaEventLogger;
    }

    public Container augmentContainer(byte[] containerBytes, Container eeContainer, String containerName) {
        Container euContainer = ContainerUtil.createContainer(containerBytes, euConfiguration);

        // Container must contain at least 1 signature
        validateNotEmpty(eeContainer);

        // Container must contain at least 1 Estonian signature
        List<Signature> estonianSignatures = findEstonianSignaturesOrFail(eeContainer);

        // Validate container with both Estonian and EU configuration
        ContainerValidationResult eeValidationResult = eeContainer.validate();
        ContainerValidationResult euValidationResult = euContainer.validate();

        // Only personal signatures can be augmented, not e-seals
        List<Signature> signaturesWithoutESeals = findPersonalSignaturesOrFail(estonianSignatures, eeValidationResult);

        // Containers with LT_TM signatures must be wrapped into ASiC-S
        if (containLtTmSignatures(signaturesWithoutESeals, eeValidationResult)) {
            return wrapAsiceIntoAsics(containerBytes, containerName);
        }

        // Only signatures with LT and LTA profile can be augmented
        List<Signature> eeSignaturesWithAugmentableProfile = getSignaturesWithAugmentableProfile(signaturesWithoutESeals, eeValidationResult);
        List<Signature> euSignaturesWithAugmentableProfile = getSignaturesWithAugmentableProfile(signaturesWithoutESeals, euValidationResult);
        // If there is at least 1 Estonian personal signature that is not augmentable, the container will not be augmented to LTA,
        // but may still be eligible for wrapping it into an ASiC-S container
        if (eeSignaturesWithAugmentableProfile.size() < signaturesWithoutESeals.size()
                || euSignaturesWithAugmentableProfile.size() < signaturesWithoutESeals.size()) {
            if (euSignaturesWithAugmentableProfile.size() == 0 && eeSignaturesWithAugmentableProfile.size() == 0) {
                throw new InvalidSessionDataException("Unable to augment. Container does not contain any Estonian signatures with LT or LTA profile");
            } else if (eeSignaturesWithAugmentableProfile.size() > 0) {
                return wrapAsiceIntoAsics(containerBytes, containerName);
            } else {
                // if eeSignaturesWithAugmentableProfile.size() == 0 && euSignaturesWithAugmentableProfile.size() > 0:
                throw new NotImplementedException("Not implemented!");
            }
        }

        // Signatures must be valid
        if (!areSignaturesValid(eeContainer, eeValidationResult, eeSignaturesWithAugmentableProfile, euValidationResult, euSignaturesWithAugmentableProfile)) {
            return wrapAsiceIntoAsics(containerBytes, containerName);
        }

        try (ServiceAccessScope ignored = new ServiceAccessScope(createServiceAccessListener())) {
            eeContainer.extendSignatureProfile(SignatureProfile.LTA, eeSignaturesWithAugmentableProfile);
        }
        return eeContainer;
    }

    private Container wrapAsiceIntoAsics(byte[] container, String containerName) {
        Container asicsContainer = new AsicSContainerBuilder()
                .withConfiguration(eeConfiguration)
                .withDataFile(new ByteArrayInputStream(container), containerName, MimeTypeEnum.ASICE.getMimeTypeString())
                .build();
        try (ServiceAccessScope ignored = new ServiceAccessScope(createServiceAccessListener())) {
            Timestamp timestamp = TimestampBuilder.aTimestamp(asicsContainer)
                    .invokeTimestamping();
            asicsContainer.addTimestamp(timestamp);
        }
        return asicsContainer;
    }

    private static boolean containLtTmSignatures(List<Signature> signatures, ContainerValidationResult eeValidationResult) {
        return signatures.stream()
                .map(signature -> getSignatureReport(signature, eeValidationResult).getSignatureFormat())
                .anyMatch(SignatureLevel.XAdES_BASELINE_LT_TM::equals);
    }

    private boolean areSignaturesValid(Container container, ContainerValidationResult eeValidationResult,
                                       List<Signature> eeSignatures,
                                       ContainerValidationResult euValidationResult,
                                       List<Signature> euSignatures) {
        for (Signature eeSignature : eeSignatures) {
            // TODO: Test with container which has duplicate signature ID-s!
            SignatureValidationReport eeReport = getSignatureReport(eeSignature, eeValidationResult);
            if (!Indication.TOTAL_PASSED.equals(eeReport.getIndication())
                    || !SignatureQualification.QESIG.equals(eeReport.getSignatureLevel().getValue())) {
                return false;
            }
        }
        for (Signature euSignature : euSignatures) {
            SignatureValidationReport euReport = getSignatureReport(euSignature, euValidationResult);
            if (!Indication.TOTAL_PASSED.equals(euReport.getIndication())) {
                return false;
            }
        }
        try {
            ensureDssValidationPasses(eeSignatures, container);
        } catch (AlertException e) {
            return false;
        }
        return true;
    }

    private void ensureDssValidationPasses(List<Signature> signatures, Container container) {
        DetachedContentCreator detachedContentCreator;
        try {
            detachedContentCreator = new DetachedContentCreator().populate(container.getDataFiles());
        } catch (Exception e) {
            log.error("Error in datafiles processing: {}", e.getMessage());
            throw new InvalidSessionDataException("Failed to process datafiles in the container");
        }
        List<DSSDocument> detachedContentList = detachedContentCreator.getDetachedContentList();
        XadesValidationDssFacade validationFacade = new XadesValidationDssFacade(detachedContentList, eeConfiguration);
        for (Signature signature : signatures) {
            validateSignatureWithDss(validationFacade, signature);
        }
    }

    private static void validateSignatureWithDss(XadesValidationDssFacade validationFacade, Signature signature) {
        AsicSignature asicSignature = (AsicSignature) signature;
        DSSDocument signatureDocument = asicSignature.getSignatureDocument();
        XAdESSignature dssSignature = asicSignature.getOrigin().getDssSignature();
        validationFacade.openXadesValidator(signatureDocument)
                .getValidationData(Collections.singletonList(dssSignature));
    }

    private static SignatureValidationReport getSignatureReport(Signature signature, ContainerValidationResult validationResult) {
        return validationResult.getReports().stream()
                .filter(report -> report.getUniqueId().equals(signature.getUniqueId()))
                .findFirst()
                .orElseThrow(() -> new InvalidSessionDataException("Validation report not found for signature " + signature.getUniqueId()));
    }

    private static List<Signature> getSignaturesWithAugmentableProfile(
            List<Signature> signatures,
            ContainerValidationResult validationResult) {
        return signatures.stream()
                .filter(signature -> isAugmentableProfile(getSignatureReport(signature, validationResult).getSignatureFormat()))
                .toList();
    }

    private static boolean isAugmentableProfile(SignatureLevel signatureLevel) {
        if (!augmentableSignatureProfiles.contains(signatureLevel)) {
            log.warn("Cannot augment signature profile {}", signatureLevel);
            return false;
        }
        return true;
    }

    private static List<Signature> findEstonianSignaturesOrFail(Container container) {
        List<Signature> estonianSignatures = container.getSignatures().stream()
                .filter(signature -> "EE".equals(signature.getSigningCertificate().getSubjectName(X509Cert.SubjectName.C)))
                .toList();
        if (estonianSignatures.isEmpty()) {
            throw new InvalidSessionDataException("Unable to augment. Container does not contain any Estonian signatures");
        }
        return estonianSignatures;
    }

    private static List<Signature> findPersonalSignaturesOrFail(List<Signature> signatures, ContainerValidationResult eeValidationResult) {
        List<Signature> personalSignatures = new ArrayList<>(signatures.size());
        for (Signature signature: signatures) {
            SignatureQualification signatureQualification = eeValidationResult.getSignatureQualification(signature.getUniqueId());
            if (signatureQualification.getReadable().contains("Sig")) {
                personalSignatures.add(signature);
            }
        }
        if (personalSignatures.isEmpty()) {
            throw new InvalidSessionDataException("Unable to augment. Container contains only e-seals, but no Estonian personal signatures");
        }
        return personalSignatures;
    }

    private static void validateNotEmpty(Container container) {
        if (container.getSignatures().isEmpty()) {
            throw new InvalidSessionDataException("Unable to augment. Container does not contain any signatures");
        }
    }

    private ServiceAccessListener createServiceAccessListener() {
        return e -> {
            if (ServiceType.TSP == e.getServiceType()) {
                sigaEventLogger.logEvent(SigaEvent.buildEventWithParameter(SigaEventName.TSA_REQUEST, REQUEST_URL, e.getServiceUrl()));
            } else {
                sigaEventLogger.logEvent(SigaEvent.buildEventWithParameter(SigaEventName.OCSP_REQUEST, REQUEST_URL, e.getServiceUrl()));
            }
        };
    }
}
