package ee.openeid.siga.service.signature;

import ee.openeid.siga.webapp.json.CreateContainerRequest;
import ee.openeid.siga.webapp.json.CreateContainerResponse;

public interface ContainerService {
    CreateContainerResponse createContainer(CreateContainerRequest request);
}
