package ee.openeid.siga.common.session;

import ee.openeid.siga.common.HashCodeDataFile;
import ee.openeid.siga.common.SignatureWrapper;
import ee.openeid.siga.common.SigningType;
import lombok.Builder;
import lombok.Data;
import org.digidoc4j.DataToSign;

import java.util.List;

@Data
@Builder
public class DetachedDataFileContainerSessionHolder implements Session {
    private List<HashCodeDataFile> dataFiles;
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
