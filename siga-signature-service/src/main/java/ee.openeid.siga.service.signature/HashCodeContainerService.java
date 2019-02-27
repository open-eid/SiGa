package ee.openeid.siga.service.signature;

import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.common.session.HashCodeContainerSessionHolder;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.service.signature.hashcode.HashCodeContainer;
import ee.openeid.siga.service.signature.session.SessionIdGenerator;
import ee.openeid.siga.service.signature.util.ContainerUtil;
import ee.openeid.siga.session.HashCodeSessionService;
import ee.openeid.siga.webapp.json.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Base64;

@Service
public class HashCodeContainerService {

    private HashCodeSessionService sessionService;

    public CreateHashCodeContainerResponse createContainer(CreateHashCodeContainerRequest request) {

        HashCodeContainer hashCodeContainer = new HashCodeContainer();
        request.getDataFiles().forEach(dataFile ->
                hashCodeContainer.addDataFile(ContainerUtil.transformDataFileToHashCodeDataFile(dataFile))
        );
        OutputStream outputStream = new ByteArrayOutputStream();
        hashCodeContainer.save(outputStream);

        String sessionId = SessionIdGenerator.generateSessionId();
        sessionService.update(sessionId, transformContainerToSession(hashCodeContainer, request.getContainerName()));
        CreateHashCodeContainerResponse response = new CreateHashCodeContainerResponse();
        response.setContainerId(sessionId);
        return response;
    }

    public UploadHashCodeContainerResponse uploadContainer(UploadHashCodeContainerRequest request) {
        String sessionId = SessionIdGenerator.generateSessionId();
        HashCodeContainer hashCodeContainer = new HashCodeContainer();
        InputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(request.getContainer().getBytes()));
        hashCodeContainer.open(inputStream);
        sessionService.update(sessionId, transformContainerToSession(hashCodeContainer, request.getContainerName()));

        UploadHashCodeContainerResponse response = new UploadHashCodeContainerResponse();
        response.setContainerId(sessionId);
        return response;
    }

    public GetHashCodeContainerResponse getContainer(String containerId) {
        Session session = sessionService.getContainer(containerId);
        if (session instanceof HashCodeContainerSessionHolder) {
            HashCodeContainerSessionHolder sessionHolder = (HashCodeContainerSessionHolder) session;

            HashCodeContainer hashCodeContainer = new HashCodeContainer();
            sessionHolder.getSignatures().forEach(signatureWrapper -> hashCodeContainer.getSignatures().add(signatureWrapper));
            sessionHolder.getDataFiles().forEach(dataFile -> hashCodeContainer.getDataFiles().add(dataFile));

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            hashCodeContainer.save(outputStream);
            byte[] container = outputStream.toByteArray();

            GetHashCodeContainerResponse response = new GetHashCodeContainerResponse();
            response.setContainer(new String(Base64.getEncoder().encode(container)));
            response.setContainerName(sessionHolder.getContainerName());
            return response;
        }
        throw new TechnicalException("Unable to parse session");
    }


    private HashCodeContainerSessionHolder transformContainerToSession(HashCodeContainer container, String containerName) {
        return HashCodeContainerSessionHolder.builder()
                .containerName(containerName)
                .dataFiles(container.getDataFiles())
                .signatures(container.getSignatures())
                .build();
    }

    @Autowired
    protected void setSessionService(HashCodeSessionService sessionService) {
        this.sessionService = sessionService;
    }


}
