package ee.openeid.siga.service.signature.container.attached;

import ee.openeid.siga.common.DataFile;
import ee.openeid.siga.common.Signature;
import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.session.AttachedDataFileContainerSessionHolder;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.service.signature.session.AttachedDataFileSessionHolder;
import ee.openeid.siga.service.signature.session.SessionIdGenerator;
import ee.openeid.siga.session.SessionResult;
import ee.openeid.siga.session.SessionService;
import eu.europa.esig.dss.MimeType;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.digidoc4j.Container.DocumentType.ASICE;

@Service
public class AttachedDataFileContainerService implements AttachedDataFileSessionHolder {

    private SessionService sessionService;

    public String createContainer(String containerName, List<DataFile> dataFiles) {
        ContainerBuilder containerBuilder = ContainerBuilder.
                aContainer(ASICE);

        dataFiles.forEach(dataFile -> containerBuilder.withDataFile(
                new ByteArrayInputStream(dataFile.getContent().getBytes()),
                dataFile.getFileName(),
                MimeType.BINARY.getMimeTypeString()
        ));

        Container container = containerBuilder.build();
        String sessionId = SessionIdGenerator.generateSessionId();
        Session session = transformContainerToSession(containerName, sessionId, container);
        sessionService.update(sessionId, session);
        return sessionId;
    }

    public String uploadContainer(String containerName, String base64container) {
        InputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(base64container.getBytes()));
        Container container = ContainerBuilder.aContainer().fromStream(inputStream).build();

        String sessionId = SessionIdGenerator.generateSessionId();
        Session session = transformContainerToSession(containerName, sessionId, container);
        sessionService.update(sessionId, session);
        return sessionId;
    }

    public String getContainer(String containerId) {
        AttachedDataFileContainerSessionHolder sessionHolder = getSessionHolder(containerId);
        Container container = sessionHolder.getContainer();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        container.save(outputStream);
        return new String(Base64.getEncoder().encode(outputStream.toByteArray()));
    }

    public List<Signature> getSignatures(String containerId) {
        AttachedDataFileContainerSessionHolder sessionHolder = getSessionHolder(containerId);
        List<Signature> signatures = new ArrayList<>();
        sessionHolder.getContainer().getSignatures().forEach(sessionSignature -> sessionHolder.getSignatureIdHolder().forEach((hashcode, generatedSignatureId) -> {
            if (sessionSignature.hashCode() == hashcode) {
                signatures.add(transformSignature(generatedSignatureId, sessionSignature));
            }
        }));
        return signatures;
    }

    public String closeSession(String containerId) {
        sessionService.remove(containerId);
        return SessionResult.OK.name();
    }

    private Signature transformSignature(String generatedSignatureId, org.digidoc4j.Signature dd4jSignature) {
        Signature signature = new Signature();
        signature.setGeneratedSignatureId(generatedSignatureId);
        signature.setId(dd4jSignature.getId());
        signature.setSignerInfo(dd4jSignature.getSigningCertificate().getSubjectName());
        signature.setSignatureProfile(dd4jSignature.getProfile().name());
        return signature;
    }

    private AttachedDataFileContainerSessionHolder transformContainerToSession(String containerName, String sessionId, Container container) {
        SigaUserDetails authenticatedUser = (SigaUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        AttachedDataFileContainerSessionHolder sessionHolder = AttachedDataFileContainerSessionHolder.builder()
                .containerName(containerName)
                .sessionId(sessionId)
                .clientName(authenticatedUser.getClientName())
                .serviceName(authenticatedUser.getServiceName())
                .serviceUuid(authenticatedUser.getServiceUuid())
                .container(container)
                .build();
        container.getSignatures().forEach(signature ->
                sessionHolder.addSignatureId(signature.hashCode(), SessionIdGenerator.generateSessionId())
        );
        return sessionHolder;
    }

    @Override
    public SessionService getSessionService() {
        return sessionService;
    }


    @Autowired
    public void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

}
