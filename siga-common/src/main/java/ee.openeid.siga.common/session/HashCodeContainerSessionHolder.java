package ee.openeid.siga.common.session;

import ee.openeid.siga.common.HashCodeDataFile;
import lombok.Builder;
import lombok.Data;
import org.digidoc4j.Signature;

import java.util.List;

@Data
@Builder
public class HashCodeContainerSessionHolder implements Session {
    private String containerName;
    private List<HashCodeDataFile> dataFiles;
    private List<Signature> signatures;
}
