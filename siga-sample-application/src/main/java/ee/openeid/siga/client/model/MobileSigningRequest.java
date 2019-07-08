package ee.openeid.siga.client.model;

import lombok.Data;

@Data
public class MobileSigningRequest {
    private boolean containerCreated;
    private ContainerType containerType;
    private String fileId;
    private String personIdentifier;
    private String phoneNr;
    private String country;

    public enum ContainerType {
        HASHCODE,
        ASIC
    }
}
