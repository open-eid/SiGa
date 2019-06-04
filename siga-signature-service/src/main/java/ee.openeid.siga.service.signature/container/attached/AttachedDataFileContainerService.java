package ee.openeid.siga.service.signature.container.attached;

import ee.openeid.siga.common.DataFile;
import ee.openeid.siga.common.Result;
import ee.openeid.siga.common.Signature;
import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.exception.ResourceNotFoundException;
import ee.openeid.siga.common.session.AttachedDataFileContainerSessionHolder;
import ee.openeid.siga.common.session.ContainerHolder;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.service.signature.session.AttachedDataFileSessionHolder;
import ee.openeid.siga.service.signature.session.SessionIdGenerator;
import ee.openeid.siga.session.SessionService;
import eu.europa.esig.dss.DSSDocument;
import eu.europa.esig.dss.InMemoryDocument;
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
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
        Container container = sessionHolder.getContainerHolder().getContainer();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        container.save(outputStream);
        return new String(Base64.getEncoder().encode(outputStream.toByteArray()));
    }

    public List<Signature> getSignatures(String containerId) {
        AttachedDataFileContainerSessionHolder sessionHolder = getSessionHolder(containerId);
        List<Signature> signatures = new ArrayList<>();
        sessionHolder.getContainerHolder().getContainer().getSignatures()
                .forEach(sessionSignature -> sessionHolder.getSignatureIdHolder()
                        .forEach((hashcode, generatedSignatureId) -> {
                            if (Arrays.hashCode(sessionSignature.getAdESSignature()) == hashcode) {
                                signatures.add(transformSignature(generatedSignatureId, sessionSignature));
                            }
                        }));
        return signatures;
    }

    public List<DataFile> getDataFiles(String containerId) {
        AttachedDataFileContainerSessionHolder sessionHolder = getSessionHolder(containerId);
        List<org.digidoc4j.DataFile> dataFiles = sessionHolder.getContainerHolder().getContainer().getDataFiles();
        return dataFiles.stream().map(this::transformDataFile).collect(Collectors.toList());
    }

    public Result addDataFile(String containerId, DataFile dataFile) {
        AttachedDataFileContainerSessionHolder sessionHolder = getSessionHolder(containerId);
        validateIfSessionMutable(sessionHolder);

        org.digidoc4j.DataFile digidoc4jDataFile = new org.digidoc4j.DataFile();
        DSSDocument dssDocument = new InMemoryDocument(dataFile.getContent().getBytes(), dataFile.getFileName());
        digidoc4jDataFile.setDocument(dssDocument);

        sessionHolder.getContainerHolder().getContainer().addDataFile(digidoc4jDataFile);
        sessionService.update(containerId, sessionHolder);
        return Result.OK;
    }

    public Result removeDataFile(String containerId, String datafileName) {
        AttachedDataFileContainerSessionHolder sessionHolder = getSessionHolder(containerId);
        validateIfSessionMutable(sessionHolder);
        Container container = sessionHolder.getContainerHolder().getContainer();
        Optional<org.digidoc4j.DataFile> dataFile = container.getDataFiles().stream()
                .filter(df -> df.getName().equals(datafileName))
                .findAny();
        if (dataFile.isEmpty()) {
            throw new ResourceNotFoundException("Data file named " + datafileName + " not found");
        }
        container.removeDataFile(dataFile.get());
        sessionService.update(containerId, sessionHolder);
        return Result.OK;
    }

    public String closeSession(String containerId) {
        sessionService.remove(containerId);
        return Result.OK.name();
    }

    private void validateIfSessionMutable(AttachedDataFileContainerSessionHolder session) {
        if (session.getContainerHolder().getContainer().getSignatures().size() != 0) {
            throw new InvalidSessionDataException("Unable to add/remove data file. Container contains signatures");
        }
    }

    private Signature transformSignature(String generatedSignatureId, org.digidoc4j.Signature dd4jSignature) {
        Signature signature = new Signature();
        signature.setGeneratedSignatureId(generatedSignatureId);
        signature.setId(dd4jSignature.getId());
        signature.setSignerInfo(dd4jSignature.getSigningCertificate().getSubjectName());
        signature.setSignatureProfile(dd4jSignature.getProfile().name());
        return signature;
    }

    private DataFile transformDataFile(org.digidoc4j.DataFile digidoc4jDataFile) {
        DataFile dataFile = new DataFile();
        dataFile.setFileName(digidoc4jDataFile.getName());
        dataFile.setContent(new String(Base64.getEncoder().encode(digidoc4jDataFile.getBytes())));
        return dataFile;
    }

    private AttachedDataFileContainerSessionHolder transformContainerToSession(String containerName, String sessionId, Container container) {
        SigaUserDetails authenticatedUser = (SigaUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        AttachedDataFileContainerSessionHolder sessionHolder = AttachedDataFileContainerSessionHolder.builder()
                .containerName(containerName)
                .sessionId(sessionId)
                .clientName(authenticatedUser.getClientName())
                .serviceName(authenticatedUser.getServiceName())
                .serviceUuid(authenticatedUser.getServiceUuid())
                .containerHolder(new ContainerHolder(container))
                .build();
        container.getSignatures().forEach(signature ->
                sessionHolder.addSignatureId(Arrays.hashCode(signature.getAdESSignature()), SessionIdGenerator.generateSessionId())
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
