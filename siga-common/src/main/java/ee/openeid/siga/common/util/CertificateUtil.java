package ee.openeid.siga.common.util;

import ee.openeid.siga.common.exception.InvalidCertificateException;
import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.common.model.KeyUsageType;
import eu.europa.esig.dss.utils.Utils;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
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
import java.util.Optional;
import java.util.stream.Stream;

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
        return certificate.getKeyUsage()[KeyUsageType.NON_REPUDIATION];
    }


    public static boolean hasProhibitedPolicies(X509Certificate certificate, List<String> policies) {
        return getPolicyOidStringsAsStream(certificate).anyMatch(policies::contains);
    }

    static Stream<String> getPolicyOidStringsAsStream(X509Certificate certificate) {
        return Optional
                .ofNullable(getCertificatePoliciesIfPresent(certificate))
                .map(CertificatePolicies::getPolicyInformation)
                .stream()
                .flatMap(Stream::of)
                .flatMap(policy -> Optional.ofNullable(policy)
                    .map(PolicyInformation::getPolicyIdentifier)
                    .map(ASN1ObjectIdentifier::getId)
                    .stream());
    }

    private static CertificatePolicies getCertificatePoliciesIfPresent(X509Certificate certificate) {
        byte[] extensionValue = certificate.getExtensionValue(Extension.certificatePolicies.getId());
        if (Utils.isArrayEmpty(extensionValue)) {
            return null;
        }
        return parseCertificatePolicies(extensionValue);
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
