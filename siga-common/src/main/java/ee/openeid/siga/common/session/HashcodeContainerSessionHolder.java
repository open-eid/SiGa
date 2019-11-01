package ee.openeid.siga.common.session;

import ee.openeid.siga.common.model.HashcodeDataFile;
import ee.openeid.siga.common.model.HashcodeSignatureWrapper;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class HashcodeContainerSessionHolder implements Session {
    @NonNull
    private String clientName;
    @NonNull
    private String serviceName;
    @NonNull
    private String serviceUuid;
    @NonNull
    private String sessionId;
    private List<HashcodeDataFile> dataFiles;
    private List<HashcodeSignatureWrapper> signatures;
    @Builder.Default
    private Map<String, DataToSignHolder> dataToSignHolder = new HashMap<>();

    @Override
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
