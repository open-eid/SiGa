package ee.openeid.siga.service.signature.client;

import ee.openeid.siga.webapp.json.ValidationConclusion;
import lombok.Data;

@Data
public class ValidationReport {
    private ValidationConclusion validationConclusion;
}
