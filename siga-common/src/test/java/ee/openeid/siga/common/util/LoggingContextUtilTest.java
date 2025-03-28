package ee.openeid.siga.common.util;

import ee.openeid.siga.common.event.SigaEventLoggingAspectTestUtil;
import org.junit.jupiter.api.Test;

import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Map;

import static ee.openeid.siga.common.util.CertificateUtilTest.ROOT_CERTIFICATE;
import static ee.openeid.siga.common.util.CertificateUtilTest.SIGNING_CERTIFICATE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LoggingContextUtilTest {

    @Test
    void addCertificatePolicyOIDsToEventLoggingContext_whenUsingSigningCertificate_EventLoggingContextContainsOIDs() {
        X509Certificate certificate = CertificateUtil.createX509Certificate(Base64.getDecoder().decode(SIGNING_CERTIFICATE.getBytes()));

        Map<String, String> contextMap = SigaEventLoggingAspectTestUtil.executeAndReturnContext(
                () -> LoggingContextUtil.addCertificatePolicyOIDsToEventLoggingContext(certificate)
        );

        assertThat(contextMap, equalTo(Map.of(
                "certificate_policies", "1.3.6.1.4.1.51361.1.2.1|0.4.0.194112.1.2"
        )));
    }

    @Test
    void addCertificatePolicyOIDsToEventLoggingContext_whenUsingRootCertificate_EventLoggingContextIsEmpty() {
        X509Certificate certificate = CertificateUtil.createX509Certificate(Base64.getDecoder().decode(ROOT_CERTIFICATE.getBytes()));

        Map<String, String> contextMap = SigaEventLoggingAspectTestUtil.executeAndReturnContext(
                () -> LoggingContextUtil.addCertificatePolicyOIDsToEventLoggingContext(certificate)
        );

        assertTrue(contextMap.isEmpty());
    }

    @Test
    void addCertificatePolicyOIDsToEventLoggingContext_whenUsingSigningCertificate_ThrowsException() {
        X509Certificate certificate = CertificateUtil.createX509Certificate(Base64.getDecoder().decode(SIGNING_CERTIFICATE.getBytes()));

        IllegalStateException caughtException = assertThrows(
                IllegalStateException.class,
                () -> LoggingContextUtil.addCertificatePolicyOIDsToEventLoggingContext(certificate)
        );

        assertEquals("Cannot add event logging aspect context parameter outside of logging context", caughtException.getMessage());
    }
}
