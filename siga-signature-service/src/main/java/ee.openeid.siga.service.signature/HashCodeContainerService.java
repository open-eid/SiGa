package ee.openeid.siga.service.signature;

import ee.openeid.siga.common.session.HashCodeContainerSessionHolder;
import ee.openeid.siga.service.signature.hashcode.HashCodeContainer;
import ee.openeid.siga.service.signature.session.SessionIdGenerator;
import ee.openeid.siga.service.signature.util.ContainerUtil;
import ee.openeid.siga.session.HashCodeSessionService;
import ee.openeid.siga.webapp.json.CreateContainerRequest;
import ee.openeid.siga.webapp.json.CreateContainerResponse;
import ee.openeid.siga.webapp.json.UploadContainerRequest;
import ee.openeid.siga.webapp.json.UploadContainerResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

@Component
public class HashCodeContainerService implements ContainerService {

    private HashCodeSessionService sessionService;

    public CreateContainerResponse createContainer(CreateContainerRequest request) {

        HashCodeContainer hashCodeContainer = new HashCodeContainer();
        request.getDataFiles().forEach(dataFile ->
                hashCodeContainer.addDataFile(ContainerUtil.transformDataFileToHashCodeDataFile(dataFile))
        );
        OutputStream outputStream = new ByteArrayOutputStream();
        hashCodeContainer.save(outputStream);

        String sessionId = SessionIdGenerator.generateSessionId();
        sessionService.update(sessionId, transformContainerToSession(hashCodeContainer, request.getContainerName()));
        CreateContainerResponse response = new CreateContainerResponse();
        response.setSessionId(sessionId);
        return response;
    }

    @Override
    public UploadContainerResponse uploadContainer(UploadContainerRequest request) {
        String sessionId = SessionIdGenerator.generateSessionId();
        HashCodeContainer hashCodeContainer = new HashCodeContainer();
        InputStream inputStream = new ByteArrayInputStream(request.getContainer().getBytes());
        hashCodeContainer.open(inputStream);
        sessionService.update(sessionId, transformContainerToSession(hashCodeContainer, request.getContainerName()));

        UploadContainerResponse response = new UploadContainerResponse();
        response.setSessionId(sessionId);
        return response;
    }

    private HashCodeContainerSessionHolder transformContainerToSession(HashCodeContainer container, String containerName) {
        return HashCodeContainerSessionHolder.builder()
                .containerName(containerName)
                .dataFiles(container.getDataFiles())
                .signatures(container.getSignatures())
                .build();
    }

    @Autowired
    public void setSessionService(HashCodeSessionService sessionService) {
        this.sessionService = sessionService;
    }
}
