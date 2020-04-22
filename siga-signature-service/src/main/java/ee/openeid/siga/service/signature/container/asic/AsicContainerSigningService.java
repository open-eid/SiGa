package ee.openeid.siga.service.signature.container.asic;

import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.session.AsicContainerSessionHolder;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.service.signature.container.ContainerSigningService;
import ee.openeid.siga.service.signature.session.AsicSessionHolder;
import ee.openeid.siga.service.signature.util.ContainerUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.DataFile;
import org.digidoc4j.DataToSign;
import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.Signature;
import org.digidoc4j.SignatureBuilder;
import org.digidoc4j.SignatureParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

@Service
@Profile("datafileContainer")
public class AsicContainerSigningService extends ContainerSigningService implements AsicSessionHolder {
    private Configuration configuration;

    @Override
    public DataToSign buildDataToSign(Session session, SignatureParameters signatureParameters) {
        AsicContainerSessionHolder asicContainerSessionHolder = (AsicContainerSessionHolder) session;
        Container container = ContainerUtil.createContainer(asicContainerSessionHolder.getContainer(), configuration);

        SignatureBuilder signatureBuilder = buildSignatureBuilder(container, signatureParameters);
        return signatureBuilder.buildDataToSign();
    }

    @Override
    public Session getSession(String containerId) {
        return getSessionHolder(containerId);
    }

    @Override
    public void addSignatureToSession(Session sessionHolder, Signature signature, String signatureId) {
        AsicContainerSessionHolder asicContainerSessionHolder = (AsicContainerSessionHolder) sessionHolder;
        Container container = ContainerUtil.createContainer(asicContainerSessionHolder.getContainer(), configuration);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        container.addSignature(signature);
        container.save(byteArrayOutputStream);
        asicContainerSessionHolder.setContainer(byteArrayOutputStream.toByteArray());
        asicContainerSessionHolder.addSignatureId(signatureId, Arrays.hashCode(signature.getAdESSignature()));
        asicContainerSessionHolder.clearSigning(signatureId);
    }

    @Override
    public void verifySigningObjectExistence(Session session) {
        AsicContainerSessionHolder sessionHolder = (AsicContainerSessionHolder) session;
        Container container = ContainerUtil.createContainer(sessionHolder.getContainer(), configuration);
        verifyContainerExistence(container);
    }

    @Override
    public String generateDataFilesHash(Session session) {
        Container container = ContainerUtil.createContainer(((AsicContainerSessionHolder) session).getContainer(), configuration);
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

    private void verifyContainerExistence(Container container) {
        if (container == null) {
            throw new InvalidSessionDataException("Unable to create signature. Container must exist");
        }
    }


    @Autowired
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

}
