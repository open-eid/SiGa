package ee.openeid.siga.client.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsicContainerWrapper implements Serializable {

    private String id;
    private String name;
    private byte[] container;
}
