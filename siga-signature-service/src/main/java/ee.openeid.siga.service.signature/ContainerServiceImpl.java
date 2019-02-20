package ee.openeid.siga.service.signature;

import ee.openeid.siga.common.ContainerType;
import ee.openeid.siga.common.ContainerWrapper;
import ee.openeid.siga.common.exception.InvalidRequestException;
import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.service.signature.hashcode.HashCodeContainer;
import ee.openeid.siga.service.signature.session.SessionIdGenerator;
import ee.openeid.siga.session.SessionService;
import ee.openeid.siga.webapp.json.CreateContainerRequest;
import ee.openeid.siga.webapp.json.CreateContainerResponse;
import ee.openeid.siga.webapp.json.DataFile;
import eu.europa.esig.dss.MimeType;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.digidoc4j.ContainerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import static org.digidoc4j.Container.DocumentType.ASICE;

@Component
public class ContainerServiceImpl implements ContainerService {

    private SessionService sessionService;

    @Override
    public CreateContainerResponse createContainer(CreateContainerRequest request) {
        ContainerType containerType = getContainerType(request.getDataFiles());
        byte[] container = createContainer(containerType, request.getDataFiles());
        ContainerWrapper containerWrapper = new ContainerWrapper(request.getContainerName(), container);
        String sessionId = SessionIdGenerator.generateSessionId();
        sessionService.update(sessionId, containerWrapper);
        CreateContainerResponse response = new CreateContainerResponse();
        response.setSessionId(sessionId);
        return response;
    }

    protected byte[] createContainer(ContainerType containerType, List<DataFile> dataFiles) {
        byte[] container;
        if (containerType == ContainerType.ATTACHED) {
            try {
                container = createAttachedContainer(dataFiles);
            } catch (IOException e) {
                throw new TechnicalException("Could not create container");
            }
        } else {
            container = createDetachedContainer(dataFiles);
        }
        return container;
    }

    private ContainerType getContainerType(List<DataFile> dataFiles) {
        boolean isAttached = isAttachedContainerRequest(dataFiles);
        if (isAttached)
            return ContainerType.ATTACHED;
        boolean isDetached = isDetachedContainerRequest(dataFiles);
        if (isDetached)
            return ContainerType.DETACHED;
        throw new InvalidRequestException("Could not determine container type");
    }

    private byte[] createDetachedContainer(List<DataFile> dataFiles) {
        HashCodeContainer hashCodeContainer = new HashCodeContainer();
        dataFiles.forEach(dataFile -> hashCodeContainer.getDataFiles().add(dataFile));
        OutputStream outputStream = new ByteArrayOutputStream();
        hashCodeContainer.save(outputStream);
        return ((ByteArrayOutputStream) outputStream).toByteArray();
    }

    private byte[] createAttachedContainer(List<DataFile> dataFiles) throws IOException {
        ContainerBuilder containerBuilder = ContainerBuilder.
                aContainer(ASICE);
        dataFiles.forEach(dataFile -> containerBuilder.withDataFile(
                new ByteArrayInputStream(dataFile.getFileContent().getBytes()),
                dataFile.getFileName(),
                MimeType.BINARY.getMimeTypeString()
        ));
        return IOUtils.toByteArray(containerBuilder.build().saveAsStream());
    }

    private boolean isAttachedContainerRequest(List<DataFile> dataFiles) {
        return dataFiles.stream().allMatch(dataFile ->
                StringUtils.isNotBlank(dataFile.getFileContent())
                        && StringUtils.isBlank(dataFile.getFileHashSha256())
                        && StringUtils.isBlank(dataFile.getFileHashSha512())
                        && dataFile.getFileSize() == null
                        && StringUtils.isNotBlank(dataFile.getFileName()));
    }

    private boolean isDetachedContainerRequest(List<DataFile> dataFiles) {
        return dataFiles.stream().allMatch(dataFile ->
                StringUtils.isBlank(dataFile.getFileContent())
                        && StringUtils.isNotBlank(dataFile.getFileHashSha256())
                        && StringUtils.isNotBlank(dataFile.getFileHashSha512())
                        && dataFile.getFileSize() > 0
                        && StringUtils.isNotBlank(dataFile.getFileName()));
    }

    @Autowired
    public void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }
}
