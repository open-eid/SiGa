package ee.openeid.siga.service.signature;

import ee.openeid.siga.session.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AttachedDataFileContainerService { //implements ContainerService { TODO: update wadl/implementation

    private SessionService sessionService;
//
//    @Override
//    public CreateDataFileContainerResponse createContainer(CreateHashCodeContainerRequest request) {
//        ContainerBuilder containerBuilder = ContainerBuilder.
//                aContainer(ASICE);
//        request.getDataFiles().forEach(dataFile -> containerBuilder.withDataFile(
//                new ByteArrayInputStream(dataFile.getFileContent().getBytes()),
//                dataFile.getFileName(),
//                MimeType.BINARY.getMimeTypeString()
//        ));
//
//        Container container = containerBuilder.build();
//        String sessionId = SessionIdGenerator.generateSessionId();
//        DataFileContainerSessionHolder session = new DataFileContainerSessionHolder(request.getContainerName(), container);
//        sessionService.update(sessionId, session);
//        CreateContainerResponse response = new CreateContainerResponse();
//        response.setSessionId(sessionId);
//        return response;
//
//    }

//    @Override
//    public UploadContainerResponse uploadContainer(UploadContainerRequest request) {
//        return null;
//    }

    @Autowired
    protected void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

}
