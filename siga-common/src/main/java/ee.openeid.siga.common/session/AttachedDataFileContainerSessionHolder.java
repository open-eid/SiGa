package ee.openeid.siga.common.session;

import ee.openeid.siga.common.SignatureWrapper;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.digidoc4j.DataFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class AttachedDataFileContainerSessionHolder implements Session {
    @NonNull
    private String clientName;
    @NonNull
    private String serviceName;
    @NonNull
    private String serviceUuid;
    @NonNull
    private String sessionId;
    private List<DataFile> dataFiles;
    private List<SignatureWrapper> signatures;
    @Getter(AccessLevel.PRIVATE)
    @Setter(AccessLevel.PRIVATE)
    @Builder.Default
    private Map<String, DataToSignHolder> dataToSignHolder = new HashMap<>();

    public void addDataToSign(String signatureId, DataToSignHolder dataToSign) {
        this.dataToSignHolder.put(signatureId, dataToSign);
    }

    public DataToSignHolder getDataToSignHolder(String signatureId) {
        return dataToSignHolder.get(signatureId);
    }

    public DataToSignHolder clearSigning(String signatureId) {
        return dataToSignHolder.remove(signatureId);
    }
}
