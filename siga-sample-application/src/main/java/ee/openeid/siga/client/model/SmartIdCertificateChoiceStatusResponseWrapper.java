package ee.openeid.siga.client.model;

import ee.openeid.siga.webapp.json.GetContainerSmartIdCertificateChoiceStatusResponse;
import lombok.Data;

@Data
public class SmartIdCertificateChoiceStatusResponseWrapper {
    private GetContainerSmartIdCertificateChoiceStatusResponse response;
    private boolean pollingSuccess;
}
