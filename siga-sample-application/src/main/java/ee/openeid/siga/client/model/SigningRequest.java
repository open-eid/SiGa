package ee.openeid.siga.client.model;

import lombok.Data;

@Data
public abstract class SigningRequest {
    private ContainerType containerType;
    private String containerId;

    public enum ContainerType {
        HASHCODE,
        ASIC
    }
}
