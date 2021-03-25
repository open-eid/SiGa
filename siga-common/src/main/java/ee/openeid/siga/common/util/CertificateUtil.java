package ee.openeid.siga.common.util;

import ee.openeid.siga.common.exception.InvalidCertificateException;
import ee.openeid.siga.common.exception.TechnicalException;
import eu.europa.esig.dss.utils.Utils;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x509.CertificatePolicies;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.PolicyInformation;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

@Slf4j
@UtilityClass
public class CertificateUtil {

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
        Date currentDate = new Date();
        return certificate.getNotAfter().after(currentDate) && certificate.getNotBefore().before(currentDate);
    }

    public static boolean isSigningCertificate(X509Certificate certificate) {
        if (certificate.getKeyUsage() == null || certificate.getKeyUsage().length < 2) {
            return false;
        }
        return certificate.getKeyUsage()[1];
    }


    public static boolean hasProhibitedPolicies(X509Certificate certificate, List<String> policies) {
        byte[] extensionValue = certificate.getExtensionValue(Extension.certificatePolicies.getId());
        if (Utils.isArrayEmpty(extensionValue)) {
            return false;
        }
        CertificatePolicies certificatePolicies = parseCertificatePolicies(extensionValue);
        for (PolicyInformation policyInformation : certificatePolicies.getPolicyInformation()) {
            if (policyInformation == null || policyInformation.getPolicyIdentifier() == null) {
                continue;
            }
            if (policies.contains(policyInformation.getPolicyIdentifier().getId())) {
                return true;
            }
        }
        return false;
    }

    private static CertificatePolicies parseCertificatePolicies(byte[] extensionValue) {
        try {
            return CertificatePolicies.getInstance(JcaX509ExtensionUtils.parseExtensionValue(extensionValue));
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new TechnicalException("Could not parse certificate extension value");
        }
    }
}
