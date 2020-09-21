package ee.openeid.siga.common.session;

import java.security.cert.X509Certificate;

public interface Session {
    String getClientName();

    String getServiceName();

    String getServiceUuid();

    String getSessionId();

    void addDataToSign(String signatureId, DataToSignHolder dataToSign);

    void addCertificateSessionId(String certificateId, String sessionId);

    String getCertificateSessionId(String certificateId);

    void addCertificate(String documentNumber, X509Certificate certificate);

    X509Certificate getCertificate(String documentNumber);

    DataToSignHolder getDataToSignHolder(String signatureId);

    DataToSignHolder clearSigning(String signatureId);

    String clearCertificateSessionId(String certificateId);

    X509Certificate clearCertificate(String documentNumber);
}
