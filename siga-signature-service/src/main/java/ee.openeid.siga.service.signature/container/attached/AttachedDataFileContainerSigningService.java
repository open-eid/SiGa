package ee.openeid.siga.service.signature.container.attached;

import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.session.AttachedDataFileContainerSessionHolder;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.service.signature.container.ContainerSigningService;
import ee.openeid.siga.service.signature.session.AttachedDataFileSessionHolder;
import org.digidoc4j.Container;
import org.digidoc4j.DataToSign;
import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.Signature;
import org.digidoc4j.SignatureBuilder;
import org.digidoc4j.SignatureParameters;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
public class AttachedDataFileContainerSigningService extends ContainerSigningService implements AttachedDataFileSessionHolder {

    @Override
    public DataToSign buildDataToSign(Session session, SignatureParameters signatureParameters) {
        Container container = ((AttachedDataFileContainerSessionHolder) session).getContainerHolder().getContainer();
        SignatureBuilder signatureBuilder = buildSignatureBuilder(container, signatureParameters);
        return signatureBuilder.buildDataToSign();
    }

    @Override
    public Session getSession(String containerId) {
        return getSessionHolder(containerId);
    }

    @Override
    public void addSignatureToSession(Session sessionHolder, Signature signature, String signatureId) {
        AttachedDataFileContainerSessionHolder attachedDataFileContainerSessionHolder = (AttachedDataFileContainerSessionHolder) sessionHolder;

        attachedDataFileContainerSessionHolder.getContainerHolder().getContainer().addSignature(signature);
        attachedDataFileContainerSessionHolder.addSignatureId( signatureId, Arrays.hashCode(signature.getAdESSignature()));
        attachedDataFileContainerSessionHolder.clearSigning(signatureId);
    }

    @Override
    public void verifySigningObjectExistence(Session session) {
        verifyContainerExistence((AttachedDataFileContainerSessionHolder) session);
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

    private void verifyContainerExistence(AttachedDataFileContainerSessionHolder sessionHolder) {
        if (sessionHolder.getContainerHolder().getContainer() == null) {
            throw new InvalidSessionDataException("Unable to create signature. Container must exists");
        }
    }
}
