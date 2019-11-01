package ee.openeid.siga.common.session;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Builder
public class AsicContainerSessionHolder implements Session {
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
    @Setter
    private byte[] container;
    @Builder.Default
    private Map<String, Integer> signatureIdHolder = new HashMap<>();
    @Builder.Default
    private Map<String, DataToSignHolder> dataToSignHolder = new HashMap<>();

    @Override
    public void addDataToSign(String signatureId, DataToSignHolder dataToSign) {
        this.dataToSignHolder.put(signatureId, dataToSign);
    }

    public void addSignatureId(String signatureId, Integer hash) {
        this.signatureIdHolder.put(signatureId, hash);
    }

    public DataToSignHolder getDataToSignHolder(String signatureId) {
        return dataToSignHolder.get(signatureId);
    }

    public DataToSignHolder clearSigning(String signatureId) {
        return dataToSignHolder.remove(signatureId);
    }

}
