package ee.openeid.siga.common.session;

import ee.openeid.siga.common.model.HashcodeDataFile;
import ee.openeid.siga.common.model.HashcodeSignatureWrapper;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

import java.security.cert.X509Certificate;
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
    @Builder.Default
    private Map<String, String> certificateSessionHolder = new HashMap<>();
    @Builder.Default
    private Map<String, X509Certificate> certificateHolder = new HashMap<>();
    @Override
    public void addDataToSign(String signatureId, DataToSignHolder dataToSign) {
        dataToSignHolder.put(signatureId, dataToSign);
    }

    @Override
    public void addCertificateSessionId(String certificateId, String sessionId) {
        certificateSessionHolder.put(certificateId, sessionId);
    }

    @Override
    public String getCertificateSessionId(String certificateId) {
        return certificateSessionHolder.get(certificateId);
    }

    @Override
    public void addCertificate(String documentNumber, X509Certificate certificate) {
        certificateHolder.put(documentNumber, certificate);
    }

    @Override
    public X509Certificate getCertificate(String documentNumber) {
        return certificateHolder.get(documentNumber);
    }

    public DataToSignHolder getDataToSignHolder(String signatureId) {
        return dataToSignHolder.get(signatureId);
    }

    public DataToSignHolder clearSigning(String signatureId) {
        return dataToSignHolder.remove(signatureId);
    }

    @Override
    public String clearCertificateSessionId(String certificateId) {
        return certificateSessionHolder.remove(certificateId);
    }

    @Override
    public X509Certificate clearCertificate(String documentNumber){
        return certificateHolder.remove(documentNumber);
    }

}
