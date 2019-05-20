package ee.openeid.siga.service.signature;

import ee.openeid.siga.common.DataFile;
import ee.openeid.siga.common.SignatureWrapper;
import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.session.AttachedDataFileContainerSessionHolder;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.service.signature.session.AttachedDataFileSessionHolder;
import ee.openeid.siga.service.signature.session.SessionIdGenerator;
import ee.openeid.siga.session.SessionService;
import eu.europa.esig.dss.MimeType;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.stream.Collectors;

import static org.digidoc4j.Container.DocumentType.ASICE;

@Service
public class AttachedDataFileContainerService implements AttachedDataFileSessionHolder {

    private SessionService sessionService;

    public String createContainer(List<DataFile> dataFiles) {
        ContainerBuilder containerBuilder = ContainerBuilder.
                aContainer(ASICE);

        dataFiles.forEach(dataFile -> containerBuilder.withDataFile(
                new ByteArrayInputStream(dataFile.getContent().getBytes()),
                dataFile.getFileName(),
                MimeType.BINARY.getMimeTypeString()
        ));

        Container container = containerBuilder.build();
        String sessionId = SessionIdGenerator.generateSessionId();
        Session session = transformContainerToSession(sessionId, container);
        sessionService.update(sessionId, session);
        return sessionId;

    }

    private AttachedDataFileContainerSessionHolder transformContainerToSession(String sessionId, Container container) {
        SigaUserDetails authenticatedUser = (SigaUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        List<SignatureWrapper> signatureWrappers = container.getSignatures().stream().map(signature -> {
            SignatureWrapper signatureWrapper = new SignatureWrapper();
            signatureWrapper.setSignature(signature);
            signatureWrapper.setGeneratedSignatureId(SessionIdGenerator.generateSessionId());
            return signatureWrapper;
        }).collect(Collectors.toList());

        return AttachedDataFileContainerSessionHolder.builder()
                .sessionId(sessionId)
                .clientName(authenticatedUser.getClientName())
                .serviceName(authenticatedUser.getServiceName())
                .serviceUuid(authenticatedUser.getServiceUuid())
                .dataFiles(container.getDataFiles())
                .signatures(signatureWrappers)
                .build();
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
