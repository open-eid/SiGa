package ee.openeid.siga.common.session;

import ee.openeid.siga.common.model.HashcodeDataFile;
import ee.openeid.siga.common.model.HashcodeSignatureWrapper;
import lombok.*;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HashcodeContainerSession implements Session {
    @NonNull
    private String clientName;
    @NonNull
    private String serviceName;
    @NonNull
    private String serviceUuid;
    @NonNull
    private String sessionId;
    private List<HashcodeDataFile> dataFiles;
    @Builder.Default
    private List<HashcodeSignatureWrapper> signatures = new ArrayList<>();

    @Setter
    @Builder.Default
    private transient Map<String, SignatureSession> signatureSessions = new HashMap<>();
    @Setter
    @Builder.Default
    private transient Map<String, CertificateSession> certificateSessions = new HashMap<>();
    @Builder.Default
    private Map<String, X509Certificate> certificateHolder = new HashMap<>();

    @Override
    public void addSignatureSession(String signatureId, SignatureSession signatureSession) {
        signatureSessions.put(signatureId, signatureSession);
    }

    @Override
    public void addCertificateSession(String certificateId, CertificateSession certificateSession) {
        certificateSessions.put(certificateId, certificateSession);
    }

    @Override
    public CertificateSession getCertificateSession(String certificateId) {
        return certificateSessions.get(certificateId);
    }

    @Override
    public void addCertificate(String documentNumber, X509Certificate certificate) {
        certificateHolder.put(documentNumber, certificate);
    }

    @Override
    public X509Certificate getCertificate(String documentNumber) {
        return certificateHolder.get(documentNumber);
    }

    public SignatureSession getSignatureSession(String signatureId) {
        SignatureSession signatureSession = signatureSessions.get(signatureId);
        boolean isCleared = signatureSession != null && signatureSession.getDataToSign() == null && signatureSession.getDataFilesHash() == null;
        return isCleared ? null : signatureSession;
    }

    public void clearSigningSession(String signatureId) {
        SignatureSession signatureSession = signatureSessions.get(signatureId);
        signatureSession.setDataToSign(null);
        signatureSession.setDataFilesHash(null);
    }

    public void removeSigningSession(String signatureId) {
        signatureSessions.remove(signatureId);
    }

    public SessionStatus getSignatureSessionStatus(String signatureId) {
        SignatureSession signatureSession = signatureSessions.get(signatureId);
        return signatureSession == null ? null : signatureSession.getSessionStatus();
    }

    @Override
    public void removeCertificateSession(String certificateId) {
        certificateSessions.remove(certificateId);
    }

    @Override
    public X509Certificate clearCertificate(String documentNumber){
        return certificateHolder.remove(documentNumber);
    }
}
