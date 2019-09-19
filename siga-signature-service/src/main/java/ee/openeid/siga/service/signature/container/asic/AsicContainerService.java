package ee.openeid.siga.service.signature.container.asic;

import ee.openeid.siga.common.ContainerInfo;
import ee.openeid.siga.common.DataFile;
import ee.openeid.siga.common.Result;
import ee.openeid.siga.common.Signature;
import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.exception.InvalidContainerException;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.exception.ResourceNotFoundException;
import ee.openeid.siga.common.session.AsicContainerSessionHolder;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.service.signature.session.AsicSessionHolder;
import ee.openeid.siga.common.util.UUIDGenerator;
import ee.openeid.siga.service.signature.util.ContainerUtil;
import ee.openeid.siga.session.SessionService;
import eu.europa.esig.dss.DSSDocument;
import eu.europa.esig.dss.InMemoryDocument;
import eu.europa.esig.dss.MimeType;
import lombok.extern.slf4j.Slf4j;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.digidoc4j.Container.DocumentType.ASICE;

@Slf4j
@Service
@Profile("datafileContainer")
public class AsicContainerService implements AsicSessionHolder {

    private SessionService sessionService;
    private Configuration configuration;

    public String createContainer(String containerName, List<DataFile> dataFiles) {
        ContainerBuilder containerBuilder = ContainerBuilder.
                aContainer(ASICE).withConfiguration(configuration);

        dataFiles.forEach(dataFile -> containerBuilder.withDataFile(
                new ByteArrayInputStream(Base64.getDecoder().decode(dataFile.getContent().getBytes())),
                dataFile.getFileName(),
                MimeType.fromFileName(dataFile.getFileName()).getMimeTypeString()
        ));

        Container container = containerBuilder.build();
        String sessionId = UUIDGenerator.generateUUID();
        Session session = transformContainerToSession(containerName, sessionId, container);
        sessionService.update(sessionId, session);
        return sessionId;
    }

    public String uploadContainer(String containerName, String base64container) {
        Container container;
        try {
            container = ContainerUtil.createContainer(Base64.getDecoder().decode(base64container.getBytes()), configuration);
        } catch (Exception e) {
            log.error("Invalid container:", e);
            throw new InvalidContainerException("Invalid container");
        }
        String sessionId = UUIDGenerator.generateUUID();
        Session session = transformContainerToSession(containerName, sessionId, container);
        sessionService.update(sessionId, session);
        return sessionId;
    }

    public ContainerInfo getContainer(String containerId) {
        AsicContainerSessionHolder sessionHolder = getSessionHolder(containerId);

        ContainerInfo containerInfo = new ContainerInfo();
        containerInfo.setContainerName(sessionHolder.getContainerName());
        containerInfo.setContainer(new String(Base64.getEncoder().encode(sessionHolder.getContainer())));
        return containerInfo;
    }

    public List<Signature> getSignatures(String containerId) {
        AsicContainerSessionHolder sessionHolder = getSessionHolder(containerId);
        Container container = ContainerUtil.createContainer(sessionHolder.getContainer(), configuration);

        List<Signature> signatures = new ArrayList<>();
        container.getSignatures()
                .forEach(sessionSignature -> sessionHolder.getSignatureIdHolder()
                        .forEach((generatedSignatureId, hashcode) -> {
                            if (Arrays.hashCode(sessionSignature.getAdESSignature()) == hashcode) {
                                signatures.add(transformSignature(generatedSignatureId, sessionSignature));
                            }
                        }));
        return signatures;
    }

    public org.digidoc4j.Signature getSignature(String containerId, String signatureId) {
        AsicContainerSessionHolder sessionHolder = getSessionHolder(containerId);
        Integer signatureHashCode = sessionHolder.getSignatureIdHolder().get(signatureId);
        Container container = ContainerUtil.createContainer(sessionHolder.getContainer(), configuration);

        Optional<org.digidoc4j.Signature> digidoc4jSignature = container.getSignatures().stream()
                .filter(signature -> signatureHashCode == Arrays.hashCode(signature.getAdESSignature()))
                .findAny();

        if (digidoc4jSignature.isEmpty()) {
            throw new ResourceNotFoundException("Signature with id  " + signatureId + " not found");
        }
        return digidoc4jSignature.get();
    }

    public List<DataFile> getDataFiles(String containerId) {
        AsicContainerSessionHolder sessionHolder = getSessionHolder(containerId);
        Container container = ContainerUtil.createContainer(sessionHolder.getContainer(), configuration);

        List<org.digidoc4j.DataFile> dataFiles = container.getDataFiles();
        return dataFiles.stream().map(this::transformDataFile).collect(Collectors.toList());
    }

    public Result addDataFiles(String containerId, List<DataFile> dataFiles) {
        AsicContainerSessionHolder sessionHolder = getSessionHolder(containerId);
        Container container = ContainerUtil.createContainer(sessionHolder.getContainer(), configuration);
        validateIfSessionMutable(container);

        addDataFilesToContainer(container, dataFiles);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        container.save(outputStream);
        sessionHolder.setContainer(outputStream.toByteArray());
        sessionService.update(containerId, sessionHolder);
        return Result.OK;
    }

    public Result removeDataFile(String containerId, String datafileName) {
        AsicContainerSessionHolder sessionHolder = getSessionHolder(containerId);

        Container container = ContainerUtil.createContainer(sessionHolder.getContainer(), configuration);
        validateIfSessionMutable(container);
        Optional<org.digidoc4j.DataFile> dataFile = container.getDataFiles().stream()
                .filter(df -> df.getName().equals(datafileName))
                .findAny();
        if (dataFile.isEmpty()) {
            throw new ResourceNotFoundException("Data file named " + datafileName + " not found");
        }
        container.removeDataFile(dataFile.get());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        container.save(outputStream);
        sessionHolder.setContainer(outputStream.toByteArray());
        sessionService.update(containerId, sessionHolder);

        return Result.OK;
    }

    public String closeSession(String containerId) {
        sessionService.remove(containerId);
        return Result.OK.name();
    }

    private void addDataFilesToContainer(Container container, List<DataFile> dataFiles) {
        dataFiles.forEach(dataFile -> {
            org.digidoc4j.DataFile digidoc4jDataFile = new org.digidoc4j.DataFile();
            DSSDocument dssDocument = new InMemoryDocument(Base64.getDecoder().decode(dataFile.getContent().getBytes()), dataFile.getFileName());
            digidoc4jDataFile.setDocument(dssDocument);
            container.addDataFile(digidoc4jDataFile);
        });
    }

    private void validateIfSessionMutable(Container container) {
        if (!container.getSignatures().isEmpty()) {
            throw new InvalidSessionDataException("Unable to add/remove data file. Container contains signature(s)");
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

    private AsicContainerSessionHolder transformContainerToSession(String containerName, String sessionId, Container container) {
        SigaUserDetails authenticatedUser = (SigaUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        container.save(outputStream);
        AsicContainerSessionHolder sessionHolder = AsicContainerSessionHolder.builder()
                .containerName(containerName)
                .sessionId(sessionId)
                .clientName(authenticatedUser.getClientName())
                .serviceName(authenticatedUser.getServiceName())
                .serviceUuid(authenticatedUser.getServiceUuid())
                .container(outputStream.toByteArray())
                .build();
        container.getSignatures().forEach(signature ->
                sessionHolder.addSignatureId(UUIDGenerator.generateUUID(), Arrays.hashCode(signature.getAdESSignature()))
        );
        return sessionHolder;
    }

    @Override
    public SessionService getSessionService() {
        return sessionService;
    }

    @Autowired
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    @Autowired
    public void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

}
