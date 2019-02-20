package ee.openeid.siga.common;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ContainerWrapper {
    private String name;
    private byte[] container;
}
