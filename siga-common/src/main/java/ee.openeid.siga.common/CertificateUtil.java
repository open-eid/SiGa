package ee.openeid.siga.common;

import ee.openeid.siga.common.exception.TechnicalException;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

public class CertificateUtil {

    public static X509Certificate createX509Certificate(byte[] certificate) {
        try {
            byte[] base64DecodedCertificate = Base64.getDecoder().decode(certificate);
            CertificateFactory cf = CertificateFactory.getInstance("X509");
            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(base64DecodedCertificate));
        } catch (Exception e) {
            throw new TechnicalException("Invalid signing certificate");
        }
    }
}
