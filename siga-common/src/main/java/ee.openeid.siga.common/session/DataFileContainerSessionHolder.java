package ee.openeid.siga.common.session;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.digidoc4j.Container;


@Data
@AllArgsConstructor
public class DataFileContainerSessionHolder implements Session {
    private String containerName;
    private Container container;
}
