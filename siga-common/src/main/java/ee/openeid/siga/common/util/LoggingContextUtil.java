package ee.openeid.siga.common.util;

import ee.openeid.siga.common.event.SigaEventLoggingAspect;
import org.apache.commons.collections4.CollectionUtils;

import java.security.cert.X509Certificate;
import java.util.List;

public final class LoggingContextUtil {

    private LoggingContextUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static void addCertificatePolicyOIDsToEventLoggingContext(X509Certificate certificate) {
        List<String> policyOIDs = CertificateUtil.getPolicyOidStringsAsStream(certificate).toList();
        if (CollectionUtils.isNotEmpty(policyOIDs)) {
            String joinedOIDs = String.join("|", policyOIDs);
            SigaEventLoggingAspect.putContextParameter("certificate_policies", joinedOIDs);
        }
    }
}
