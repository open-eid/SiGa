package ee.openeid.siga.service.signature;

import ee.openeid.siga.webapp.json.CreateHashCodeContainerRequest;
import ee.openeid.siga.webapp.json.CreateHashCodeContainerResponse;
import ee.openeid.siga.webapp.json.UploadHashCodeContainerRequest;
import ee.openeid.siga.webapp.json.UploadHashCodeContainerResponse;

public interface ContainerService {

    CreateHashCodeContainerResponse createContainer(CreateHashCodeContainerRequest request);
    UploadHashCodeContainerResponse uploadContainer(UploadHashCodeContainerRequest request);
}
