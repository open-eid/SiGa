package ee.openeid.siga.common.util;

import ee.openeid.siga.common.exception.InvalidCertificateException;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;

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

    public static boolean isCertificateActive(X509Certificate certificate) {
        if (certificate.getNotAfter() == null) {
            return false;
        }
        return certificate.getNotAfter().after(new Date());
    }

    public static boolean isSigningCertificate(X509Certificate certificate) {
        if (certificate.getKeyUsage() == null || certificate.getKeyUsage().length < 2) {
            return false;
        }
        return certificate.getKeyUsage()[1];
    }
}
