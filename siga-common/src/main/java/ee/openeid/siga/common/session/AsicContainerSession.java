package ee.openeid.siga.common.session;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AsicContainerSession implements Session {
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
    private byte [] container;
    @Builder.Default
    private Map<String, Integer> signatureIdHolder = new HashMap<>();

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

    public void addSignatureId(String signatureId, Integer hash) {
        this.signatureIdHolder.put(signatureId, hash);
    }

    public SignatureSession getSignatureSession(String signatureId) {
        SignatureSession signatureSession = signatureSessions.get(signatureId);
        boolean isCleared = signatureSession != null && signatureSession.getDataToSign() == null && signatureSession.getDataFilesHash() == null;
        return isCleared ? null : signatureSession;
    }

    @Override
    public void clearSigningSession(String signatureId) {
        SignatureSession signatureSession = signatureSessions.get(signatureId);
        signatureSession.setDataToSign(null);
        signatureSession.setDataFilesHash(null);
    }

    @Override
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
