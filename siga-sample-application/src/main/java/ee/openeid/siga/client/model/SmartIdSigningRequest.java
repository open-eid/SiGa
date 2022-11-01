package ee.openeid.siga.client.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SmartIdSigningRequest extends SigningRequest {
    private String personIdentifier;
    private String country;
}
