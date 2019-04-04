package ee.openeid.siga.common.session;

import ee.openeid.siga.common.HashcodeDataFile;
import ee.openeid.siga.common.SignatureWrapper;
import ee.openeid.siga.common.SigningType;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import org.digidoc4j.DataToSign;

import java.util.List;

@Data
@Builder
public class DetachedDataFileContainerSessionHolder implements Session {
    @NonNull
    private String clientName;
    @NonNull
    private String serviceName;
    @NonNull
    private String serviceUuid;
    @NonNull
    private String sessionId;
    private List<HashcodeDataFile> dataFiles;
    private List<SignatureWrapper> signatures;
    private DataToSign dataToSign;
    private String sessionCode;
    private SigningType signingType;

    public void clearSigning() {
        this.dataToSign = null;
        this.sessionCode = null;
        this.signingType = null;
    }
}
