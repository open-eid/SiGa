package ee.openeid.siga.service.signature;

import ee.openeid.siga.webapp.json.ValidateContainerRequest;
import ee.openeid.siga.webapp.json.ValidateContainerResponse;

public interface ValidationService {
    ValidateContainerResponse validateContainer(ValidateContainerRequest request);
}
