package ee.openeid.siga.common.session;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import org.digidoc4j.Container;

@Data
@AllArgsConstructor
public class AttachedDataFileContainerSessionHolder implements Session {
    @NonNull
    private String clientName;
    @NonNull
    private String serviceName;
    @NonNull
    private String serviceUuid;
    @NonNull
    private String sessionId;
    private String containerName;
    private Container container;
}
