package ee.openeid.siga.common.session;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
public class AttachedDataFileContainerSessionHolder implements Session {
    @NonNull
    private String containerName;
    @NonNull
    private String clientName;
    @NonNull
    private String serviceName;
    @NonNull
    private String serviceUuid;
    @NonNull
    private String sessionId;
    @NonNull
    private ContainerHolder containerHolder;
    @Setter(AccessLevel.PRIVATE)
    @Builder.Default
    private Map<Integer, String> signatureIdHolder = new HashMap<>();
    @Getter(AccessLevel.PRIVATE)
    @Setter(AccessLevel.PRIVATE)
    @Builder.Default
    private Map<String, DataToSignHolder> dataToSignHolder = new HashMap<>();

    @Override
    public void addDataToSign(String signatureId, DataToSignHolder dataToSign) {
        this.dataToSignHolder.put(signatureId, dataToSign);
    }

    public void addSignatureId(Integer hash, String signatureId) {
        this.signatureIdHolder.put(hash, signatureId);
    }

    public DataToSignHolder getDataToSignHolder(String signatureId) {
        return dataToSignHolder.get(signatureId);
    }

    public DataToSignHolder clearSigning(String signatureId) {
        return dataToSignHolder.remove(signatureId);
    }

}
