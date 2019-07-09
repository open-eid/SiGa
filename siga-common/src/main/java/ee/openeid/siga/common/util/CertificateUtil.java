package ee.openeid.siga.common.util;

import ee.openeid.siga.common.exception.InvalidCertificateException;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class CertificateUtil {

    private CertificateUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static X509Certificate createX509Certificate(byte[] certificate) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X509");
            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certificate));
        } catch (Exception e) {
            throw new InvalidCertificateException("Invalid signing certificate");
        }
    }
}
