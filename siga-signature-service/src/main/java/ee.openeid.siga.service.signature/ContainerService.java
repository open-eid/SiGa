package ee.openeid.siga.service.signature;

import ee.openeid.siga.webapp.json.CreateContainerRequest;
import ee.openeid.siga.webapp.json.CreateContainerResponse;
import ee.openeid.siga.webapp.json.UploadContainerRequest;
import ee.openeid.siga.webapp.json.UploadContainerResponse;

public interface ContainerService {

    CreateContainerResponse createContainer(CreateContainerRequest request);
    UploadContainerResponse uploadContainer(UploadContainerRequest request);
}
