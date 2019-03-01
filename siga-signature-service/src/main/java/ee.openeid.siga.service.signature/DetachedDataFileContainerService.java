package ee.openeid.siga.service.signature;

import ee.openeid.siga.common.SignatureWrapper;
import ee.openeid.siga.common.session.DetachedDataFileContainerSessionHolder;
import ee.openeid.siga.service.signature.hashcode.DetachedDataFileContainer;
import ee.openeid.siga.service.signature.session.SessionIdGenerator;
import ee.openeid.siga.service.signature.util.ContainerUtil;
import ee.openeid.siga.session.SessionService;
import ee.openeid.siga.webapp.json.HashCodeDataFile;
import ee.openeid.siga.webapp.json.Signature;
import org.digidoc4j.Configuration;
import org.digidoc4j.DetachedXadesSignatureBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class DetachedDataFileContainerService implements DetachedDataFileSessionHolder {

    private SessionService sessionService;

    public String createContainer(List<HashCodeDataFile> dataFiles) {

        DetachedDataFileContainer hashCodeContainer = new DetachedDataFileContainer();
        dataFiles.forEach(dataFile ->
                hashCodeContainer.addDataFile(ContainerUtil.transformDataFileToHashCodeDataFile(dataFile))
        );
        OutputStream outputStream = new ByteArrayOutputStream();
        hashCodeContainer.save(outputStream);

        String sessionId = SessionIdGenerator.generateSessionId();
        sessionService.update(sessionId, transformContainerToSession(hashCodeContainer));
        return sessionId;
    }

    public String uploadContainer(String container) {
        String sessionId = SessionIdGenerator.generateSessionId();
        DetachedDataFileContainer hashCodeContainer = new DetachedDataFileContainer();
        InputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(container.getBytes()));
        hashCodeContainer.open(inputStream);
        sessionService.update(sessionId, transformContainerToSession(hashCodeContainer));
        return sessionId;
    }

    public String getContainer(String containerId) {
        DetachedDataFileContainerSessionHolder sessionHolder = getSession(containerId);

        DetachedDataFileContainer hashCodeContainer = new DetachedDataFileContainer();
        sessionHolder.getSignatures().forEach(signatureWrapper -> hashCodeContainer.getSignatures().add(signatureWrapper));
        sessionHolder.getDataFiles().forEach(dataFile -> hashCodeContainer.getDataFiles().add(dataFile));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        hashCodeContainer.save(outputStream);
        byte[] container = outputStream.toByteArray();

        return new String(Base64.getEncoder().encode(container));
    }

    public List<Signature> getSignatures(String containerId) {
        DetachedDataFileContainerSessionHolder sessionHolder = getSession(containerId);
        List<Signature> signatures = new ArrayList<>();
        sessionHolder.getSignatures().forEach(signatureWrapper -> {
            signatures.add(transformSignature(signatureWrapper));
        });
        return signatures;
    }

    private Signature transformSignature(SignatureWrapper signatureWrapper) {
        Signature signature = new Signature();
        DetachedXadesSignatureBuilder builder = DetachedXadesSignatureBuilder.withConfiguration(new Configuration());
        org.digidoc4j.Signature dd4jSignature = builder.openAdESSignature(signatureWrapper.getSignature());
        signature.setId(dd4jSignature.getId());
        signature.setSignatureProfile(dd4jSignature.getProfile().name());
        signature.setSignerInfo(dd4jSignature.getSigningCertificate().getSubjectName());
        return signature;
    }

    private DetachedDataFileContainerSessionHolder transformContainerToSession(DetachedDataFileContainer container) {
        return DetachedDataFileContainerSessionHolder.builder()
                .dataFiles(container.getDataFiles())
                .signatures(container.getSignatures())
                .build();
    }

    @Autowired
    protected void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public SessionService getSessionService() {
        return sessionService;
    }
}
