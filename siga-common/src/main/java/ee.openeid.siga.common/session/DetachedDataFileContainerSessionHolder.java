package ee.openeid.siga.common.session;

import ee.openeid.siga.common.HashcodeDataFile;
import ee.openeid.siga.common.SignatureWrapper;
import ee.openeid.siga.common.SigningType;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import org.digidoc4j.DataToSign;

import java.util.List;

import static lombok.AccessLevel.PRIVATE;

@Data
@Builder
@FieldDefaults(level = PRIVATE)
public class DetachedDataFileContainerSessionHolder implements Session {
    @NonNull
    String clientName;
    @NonNull
    String serviceName;
    @NonNull
    String serviceUuid;
    @NonNull
    String sessionId;
    List<HashcodeDataFile> dataFiles;
    List<SignatureWrapper> signatures;
    DataToSign dataToSign;
    String sessionCode;
    SigningType signingType;

    public void clearSigning() {
        this.dataToSign = null;
        this.sessionCode = null;
        this.signingType = null;
    }
}
