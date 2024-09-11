package ee.openeid.siga.service.signature.container.asic;

import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.session.AsicContainerSession;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.service.signature.container.ContainerSigningService;
import ee.openeid.siga.service.signature.session.AsicSessionHolder;
import ee.openeid.siga.service.signature.util.ContainerUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.digidoc4j.Constant;
import org.digidoc4j.Container;
import org.digidoc4j.DataFile;
import org.digidoc4j.DataToSign;
import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.Signature;
import org.digidoc4j.SignatureBuilder;
import org.digidoc4j.SignatureParameters;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Profile("datafileContainer")
public class AsicContainerSigningService extends ContainerSigningService implements AsicSessionHolder {

    @Override
    protected DataToSign buildDataToSign(Session session, SignatureParameters signatureParameters) {
        AsicContainerSession asicContainerSession = (AsicContainerSession) session;
        Container container = ContainerUtil.createContainer(asicContainerSession.getContainer(), configuration);

        SignatureBuilder signatureBuilder = buildSignatureBuilder(container, signatureParameters);
        return signatureBuilder.buildDataToSign();
    }

    @Override
    protected Session getSession(String containerId) {
        return getSessionHolder(containerId);
    }

    @Override
    protected void addSignatureToSession(Session sessionHolder, Signature signature, String signatureId) {
        AsicContainerSession asicContainerSession = (AsicContainerSession) sessionHolder;
        Container container = ContainerUtil.createContainer(asicContainerSession.getContainer(), configuration);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        container.addSignature(signature);
        container.save(byteArrayOutputStream);
        asicContainerSession.setContainer(byteArrayOutputStream.toByteArray());
        asicContainerSession.addSignatureId(signatureId, Arrays.hashCode(signature.getAdESSignature()));
        asicContainerSession.clearSigningSession(signatureId);
    }

    @Override
    protected void verifySigningObjectExistence(Session session) {
        AsicContainerSession sessionHolder = (AsicContainerSession) session;
        Container container = Optional
                .ofNullable(sessionHolder.getContainer())
                .map(bytes -> ContainerUtil.createContainer(bytes, configuration))
                .orElse(null);
        verifyContainerExistence(container);
        verifyContainerContainsNoEmptyDataFiles(container);
        verifyContainerType(container);
    }

    private static void verifyContainerType(Container container) {
        if (Constant.ASICS_CONTAINER_TYPE.equals(container.getType())) {
            throw new InvalidSessionDataException("ASiC-S container signing is not allowed.");
        }
    }

    @Override
    public String generateDataFilesHash(Session session) {
        Container container = ContainerUtil.createContainer(((AsicContainerSession) session).getContainer(), configuration);
        String joinedDataFiles = container.getDataFiles().stream()
                .sorted(Comparator.comparing(DataFile::getName))
                .map(dataFile -> dataFile.getName() + new String(dataFile.calculateDigest()))
                .collect(Collectors.joining());
        return new String(DigestUtils.sha256(joinedDataFiles));
    }

    private SignatureBuilder buildSignatureBuilder(Container container, SignatureParameters signatureParameters) {
        SignatureBuilder builder = SignatureBuilder.
                aSignature(container)
                .withSigningCertificate(signatureParameters.getSigningCertificate())
                .withSignatureProfile(signatureParameters.getSignatureProfile())
                .withSignatureDigestAlgorithm(DigestAlgorithm.SHA512)
                .withCountry(signatureParameters.getCountry())
                .withStateOrProvince(signatureParameters.getStateOrProvince())
                .withCity(signatureParameters.getCity())
                .withPostalCode(signatureParameters.getPostalCode());

        if (signatureParameters.getRoles() != null && !signatureParameters.getRoles().isEmpty()) {
            String[] roles = new String[signatureParameters.getRoles().size()];
            builder = builder.withRoles(signatureParameters.getRoles().toArray(roles));
        }
        return builder;
    }

    private static void verifyContainerExistence(Container container) {
        if (container == null) {
            throw new InvalidSessionDataException("Unable to create signature. Container must exist");
        }
    }

    private static void verifyContainerContainsNoEmptyDataFiles(Container container) {
        if (container.getDataFiles().stream().anyMatch(DataFile::isFileEmpty)) {
            throw new InvalidSessionDataException("Unable to sign container with empty datafiles");
        }
    }
}
