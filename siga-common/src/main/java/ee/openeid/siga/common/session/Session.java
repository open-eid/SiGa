package ee.openeid.siga.common.session;

import java.security.cert.X509Certificate;
import java.util.Map;

public interface Session {
    String getClientName();

    String getServiceName();

    String getServiceUuid();

    String getSessionId();

    void addSignatureSession(String signatureId, SignatureSession signatureSession);

    void addCertificateSession(String certificateId, CertificateSession certificateSession);

    CertificateSession getCertificateSession(String certificateId);

    void addCertificate(String documentNumber, X509Certificate certificate);

    X509Certificate getCertificate(String documentNumber);

    SignatureSession getSignatureSession(String signatureId);
    Map<String, SignatureSession> getSignatureSessions();
    void setSignatureSessions(Map<String, SignatureSession> signatureSessions);
    Map<String, CertificateSession> getCertificateSessions();
    void setCertificateSessions(Map<String, CertificateSession> certificateSessions);
    SessionStatus getSignatureSessionStatus(String signatureId);

    void clearSigningSession(String signatureId);
    void removeSigningSession(String signatureId);

    void removeCertificateSession(String certificateId);

    X509Certificate clearCertificate(String documentNumber);
}
