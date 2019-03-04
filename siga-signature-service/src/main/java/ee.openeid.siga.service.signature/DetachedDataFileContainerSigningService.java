package ee.openeid.siga.service.signature;


import ee.openeid.siga.common.HashCodeDataFile;
import ee.openeid.siga.common.exception.NoDataFileException;
import ee.openeid.siga.common.session.DetachedDataFileContainerSessionHolder;
import ee.openeid.siga.session.SessionService;
import org.digidoc4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DetachedDataFileContainerSigningService implements DetachedDataFileSessionHolder {
    private SessionService sessionService;
    private Configuration configuration = new Configuration();

    public DataToSign createDataToSign(String containerId, SignatureParameters signatureParameters) {
        DetachedDataFileContainerSessionHolder sessionHolder = getSession(containerId);
        validateContainer(sessionHolder);
        return buildDetachedXadesSignatureBuilder(sessionHolder.getDataFiles(), signatureParameters).buildDataToSign();
    }

    private DetachedXadesSignatureBuilder buildDetachedXadesSignatureBuilder(List<HashCodeDataFile> dataFiles, SignatureParameters signatureParameters) {
        DetachedXadesSignatureBuilder builder = DetachedXadesSignatureBuilder.withConfiguration(configuration)
                .withSigningCertificate(signatureParameters.getSigningCertificate())
                .withSignatureProfile(signatureParameters.getSignatureProfile())
                .withCountry(signatureParameters.getCountry())
                .withStateOrProvince(signatureParameters.getStateOrProvince())
                .withCity(signatureParameters.getCity())
                .withPostalCode(signatureParameters.getPostalCode());

        for (HashCodeDataFile hashCodeDataFile : dataFiles) {
            builder = builder.withDataFile(convertDataFile(hashCodeDataFile));
        }
        if (signatureParameters.getRoles() != null && !signatureParameters.getRoles().isEmpty()) {
            String[] roles = new String[signatureParameters.getRoles().size()];
            builder = builder.withRoles(signatureParameters.getRoles().toArray(roles));
        }
        return builder;
    }

    private DigestDataFile convertDataFile(HashCodeDataFile hashCodeDataFile) {
        String fileName = hashCodeDataFile.getFileName();
        DigestAlgorithm digestAlgorithm = DigestAlgorithm.SHA512;
        byte[] digest = hashCodeDataFile.getFileHashSha512().getBytes();
        return new DigestDataFile(fileName, digestAlgorithm, digest);
    }

    private void validateContainer(DetachedDataFileContainerSessionHolder sessionHolder) {
        if (sessionHolder.getDataFiles().size() < 1) {
            throw new NoDataFileException("Unable to create signature. Data files must be added to container");
        }
    }

    @Override
    public SessionService getSessionService() {
        return sessionService;
    }

    @Autowired
    protected void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }
}
