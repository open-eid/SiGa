package ee.openeid.siga.common.session;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import org.digidoc4j.Container;

import static lombok.AccessLevel.PRIVATE;


@Data
@AllArgsConstructor
@FieldDefaults(level = PRIVATE)
public class AttachedDataFileContainerSessionHolder implements Session {
    @NonNull
    String clientName;
    @NonNull
    String serviceName;
    @NonNull
    String serviceUuid;
    @NonNull
    String sessionId;
    String containerName;
    Container container;
}
