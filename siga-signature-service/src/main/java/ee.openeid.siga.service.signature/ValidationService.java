package ee.openeid.siga.service.signature;

import ee.openeid.siga.webapp.json.CreateHashCodeValidationReportRequest;
import ee.openeid.siga.webapp.json.CreateHashCodeValidationReportResponse;

public interface ValidationService {
    CreateHashCodeValidationReportResponse validateContainer(CreateHashCodeValidationReportRequest request);
}
