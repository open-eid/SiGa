package ee.openeid.siga.service.signature;

import ee.openeid.siga.common.session.DataFileContainerSessionHolder;
import ee.openeid.siga.service.signature.session.SessionIdGenerator;
import ee.openeid.siga.session.DataFileSessionService;
import ee.openeid.siga.webapp.json.CreateContainerRequest;
import ee.openeid.siga.webapp.json.CreateContainerResponse;
import ee.openeid.siga.webapp.json.UploadContainerRequest;
import ee.openeid.siga.webapp.json.UploadContainerResponse;
import eu.europa.esig.dss.MimeType;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

import static org.digidoc4j.Container.DocumentType.ASICE;

@Component
public class DataFileContainerService implements ContainerService {

    private DataFileSessionService sessionService;

    @Override
    public CreateContainerResponse createContainer(CreateContainerRequest request) {
        ContainerBuilder containerBuilder = ContainerBuilder.
                aContainer(ASICE);
        request.getDataFiles().forEach(dataFile -> containerBuilder.withDataFile(
                new ByteArrayInputStream(dataFile.getFileContent().getBytes()),
                dataFile.getFileName(),
                MimeType.BINARY.getMimeTypeString()
        ));

        Container container = containerBuilder.build();
        String sessionId = SessionIdGenerator.generateSessionId();
        DataFileContainerSessionHolder session = new DataFileContainerSessionHolder(request.getContainerName(), container);
        sessionService.update(sessionId, session);
        CreateContainerResponse response = new CreateContainerResponse();
        response.setSessionId(sessionId);
        return response;

    }

    @Override
    public UploadContainerResponse uploadContainer(UploadContainerRequest request) {
        return null;
    }

    @Autowired
    public void setSessionService(DataFileSessionService sessionService) {
        this.sessionService = sessionService;
    }
}
