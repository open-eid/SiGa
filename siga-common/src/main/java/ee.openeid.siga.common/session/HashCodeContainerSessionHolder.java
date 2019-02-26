package ee.openeid.siga.common.session;

import ee.openeid.siga.common.HashCodeDataFile;
import ee.openeid.siga.common.SignatureWrapper;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class HashCodeContainerSessionHolder implements Session {
    private String containerName;
    private List<HashCodeDataFile> dataFiles;
    private List<SignatureWrapper> signatures;
}
