package ee.openeid.siga.common.util;

import ee.openeid.siga.common.exception.InvalidCertificateException;
import org.junit.jupiter.api.Test;

import java.security.cert.X509Certificate;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class CertificateUtilTest {
    private static final String ROOT_CERTIFICATE = "MIIEEzCCAvugAwIBAgIQc/jtqiMEFERMtVvsSsH7sjANBgkqhkiG9w0BAQUFADB9" +
            "MQswCQYDVQQGEwJFRTEiMCAGA1UECgwZQVMgU2VydGlmaXRzZWVyaW1pc2tlc2t1" +
            "czEwMC4GA1UEAwwnVEVTVCBvZiBFRSBDZXJ0aWZpY2F0aW9uIENlbnRyZSBSb290" +
            "IENBMRgwFgYJKoZIhvcNAQkBFglwa2lAc2suZWUwIhgPMjAxMDEwMDcxMjM0NTZa" +
            "GA8yMDMwMTIxNzIzNTk1OVowfTELMAkGA1UEBhMCRUUxIjAgBgNVBAoMGUFTIFNl" +
            "cnRpZml0c2VlcmltaXNrZXNrdXMxMDAuBgNVBAMMJ1RFU1Qgb2YgRUUgQ2VydGlm" +
            "aWNhdGlvbiBDZW50cmUgUm9vdCBDQTEYMBYGCSqGSIb3DQEJARYJcGtpQHNrLmVl" +
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1gGpqCtDmNNEHUjC8LXq" +
            "xRdC1kpjDgkzOTxQynzDxw/xCjy5hhyG3xX4RPrW9Z6k5ZNTNS+xzrZgQ9m5U6uM" +
            "ywYpx3F3DVgbdQLd8DsLmuVOz02k/TwoRt1uP6xtV9qG0HsGvN81q3HvPR/zKtA7" +
            "MmNZuwuDFQwsguKgDR2Jfk44eKmLfyzvh+Xe6Cr5+zRnsVYwMA9bgBaOZMv1TwTT" +
            "VNi9H1ltK32Z+IhUX8W5f2qVP33R1wWCKapK1qTX/baXFsBJj++F8I8R6+gSyC3D" +
            "kV5N/pOlWPzZYx+kHRkRe/oddURA9InJwojbnsH+zJOa2VrNKakNv2HnuYCIonzu" +
            "pwIDAQABo4GKMIGHMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgEGMB0G" +
            "A1UdDgQWBBS1NAqdpS8QxechDr7EsWVHGwN2/jBFBgNVHSUEPjA8BggrBgEFBQcD" +
            "AgYIKwYBBQUHAwEGCCsGAQUFBwMDBggrBgEFBQcDBAYIKwYBBQUHAwgGCCsGAQUF" +
            "BwMJMA0GCSqGSIb3DQEBBQUAA4IBAQAj72VtxIw6p5lqeNmWoQ48j8HnUBM+6mI0" +
            "I+VkQr0EfQhfmQ5KFaZwnIqxWrEPaxRjYwV0xKa1AixVpFOb1j+XuVmgf7khxXTy" +
            "Bmd8JRLwl7teCkD1SDnU/yHmwY7MV9FbFBd+5XK4teHVvEVRsJ1oFwgcxVhyoviR" +
            "SnbIPaOvk+0nxKClrlS6NW5TWZ+yG55z8OCESHaL6JcimkLFjRjSsQDWIEtDvP4S" +
            "tH3vIMUPPiKdiNkGjVLSdChwkW3z+m0EvAjyD9rnGCmjeEm5diLFu7VMNVqupsbZ" +
            "SfDzzBLc5+6TqgQTOG7GaZk2diMkn03iLdHGFrh8ML+mXG9SjEPI";

    private static final String OLD_CERTIFICATE = "MIIDKDCCAhCgAwIBAgIEQi1s4zANBgkqhkiG9w0BAQUFADBfMQswCQYDVQQGEwJFRTEiMCAGA1UE" +
            "ChMZQVMgU2VydGlmaXRzZWVyaW1pc2tlc2t1czEaMBgGA1UECxMRVGVzdHNlcnRpZmlrYWFkaWQx" +
            "EDAOBgNVBAMTB1RFU1QtU0swHhcNMDUwMzA4MDkxNDEwWhcNMTIwNDA2MjEwMDAwWjB4MQswCQYD" +
            "VQQGEwJFRTEaMBgGA1UECgwRVGVzdHNlcnRpZmlrYWFkaWQxDTALBgNVBAsMBE9DU1AxJDAiBgNV" +
            "BAMMG1RFU1QtU0sgT0NTUCBSRVNQT05ERVIgMjAwNTEYMBYGCSqGSIb3DQEJARYJcGtpQHNrLmVl" +
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC2RdrY11V9ekC7Injanf4RshjWp6jf51tGOvzE" +
            "cbG2Tmyo66H7AR6hauoUygIYrbEjAVXhIznnffngJtrG69I+6cGgCNHguPlpdyAwexUPi36cfJw2" +
            "FKv9bbxyIqtxo+1uZ1XZWgcua6OXMLh0T0aZWglJ1OiAlZys6hxbOg1G8wIDAQABo1cwVTATBgNV" +
            "HSUEDDAKBggrBgEFBQcDCTAfBgNVHSMEGDAWgBQCBSfdqHKHt4LAWzkqf+M48vpSCTAdBgNVHQ4E" +
            "FgQUK9Rcl4ROS8UmFo8JH8ep5oRSJFgwDQYJKoZIhvcNAQEFBQADggEBAHZd/Bqzc7FsC05Hq8Zh" +
            "PxK4lJVMwuC+mO843jsX8cWaGMBOYWqD96gFduMjiIXVIWFpiZ1o6q+JuRbPTRhpzRvv0Yc9oMaq" +
            "j7sBhYr8mqcu0FOOMD8wlJzqFr9TZZ58ba9e/UDZPDUYvEfWEuW6giwSkPLuu0Jbz94QqmG0ErG8" +
            "8h6B14Nge7P1pR4hZfvm4I2PX8sX619OdHRf7kxS+ZXve5BHGHX5YXSPreRAvriJc/cgRFokcPyt" +
            "v7y+tebcqB+1/Dj7o2brNr+dKxIL5IseBeQD4lJ5UtvuPE7pZexUSt2EOcDAAMtHsUB30cIVwPw8" +
            "/CwfT9FBP9H3tUUCtOQ=";

    private static final String SIGNING_CERTIFICATE = "MIID6jCCA02gAwIBAgIQR+qcVFxYF1pcSy/QGEnMVjAKBggqhkjOPQQDBDBgMQswCQYDVQQG" +
            "EwJFRTEbMBkGA1UECgwSU0sgSUQgU29sdXRpb25zIEFTMRcwFQYDVQRhDA5OVFJFRS0xMDc0NzAx" +
            "MzEbMBkGA1UEAwwSVEVTVCBvZiBFU1RFSUQyMDE4MB4XDTE5MDEyNTE1NDgzMVoXDTI0MDEyNTIx" +
            "NTk1OVowfzELMAkGA1UEBhMCRUUxKjAoBgNVBAMMIUrDlUVPUkcsSkFBSy1LUklTVEpBTiwzODAw" +
            "MTA4NTcxODEQMA4GA1UEBAwHSsOVRU9SRzEWMBQGA1UEKgwNSkFBSy1LUklTVEpBTjEaMBgGA1UE" +
            "BRMRUE5PRUUtMzgwMDEwODU3MTgwdjAQBgcqhkjOPQIBBgUrgQQAIgNiAATbyCq95SWCQTr+b5MX" +
            "xRLTHYHJHCgaLornlrF9j+q6aFCDFLgoNv70yw/sHYp2FQ0yRywG2vFwDCLA5vACPLSVPGyOvYx7" +
            "fiX84uSpPo6fcNlwQ25coNfpUIIuh+T6MwujggGrMIIBpzAJBgNVHRMEAjAAMA4GA1UdDwEB/wQE" +
            "AwIGQDBIBgNVHSAEQTA/MDIGCysGAQQBg5EhAQIBMCMwIQYIKwYBBQUHAgEWFWh0dHBzOi8vd3d3" +
            "LnNrLmVlL0NQUzAJBgcEAIvsQAECMB0GA1UdDgQWBBTIgEaf0wSPZSWihjLuyTNmzm4DWzCBigYI" +
            "KwYBBQUHAQMEfjB8MAgGBgQAjkYBATAIBgYEAI5GAQQwEwYGBACORgEGMAkGBwQAjkYBBgEwUQYG" +
            "BACORgEFMEcwRRY/aHR0cHM6Ly9zay5lZS9lbi9yZXBvc2l0b3J5L2NvbmRpdGlvbnMtZm9yLXVz" +
            "ZS1vZi1jZXJ0aWZpY2F0ZXMvEwJFTjAfBgNVHSMEGDAWgBTAhJkpxE6fOwI09pnhClYACCk+ezBz" +
            "BggrBgEFBQcBAQRnMGUwLAYIKwYBBQUHMAGGIGh0dHA6Ly9haWEuZGVtby5zay5lZS9lc3RlaWQy" +
            "MDE4MDUGCCsGAQUFBzAChilodHRwOi8vYy5zay5lZS9UZXN0X29mX0VTVEVJRDIwMTguZGVyLmNy" +
            "dDAKBggqhkjOPQQDBAOBigAwgYYCQSPBHYO2O/aLmr+vqMlESJrIY3gdtWni8hd4phIl5fR3uiQa" +
            "QvtNeGBIzrGvdqgRJmYg+HvskQb/Laq7Xjp+cgqkAkEX9+x/S3H/S/+n/nogfgRSP5JCwYAw02zT" +
            "RL3MKLpZ1AOf8i1iGvpHI9S6iyXcDhh6hM8slDg7EK3KyNwfkMLh5A==";

    @Test
    public void createValidCertificate() {
        X509Certificate certificate = CertificateUtil.createX509Certificate(Base64.getDecoder().decode(ROOT_CERTIFICATE.getBytes()));
        assertEquals("EMAILADDRESS=pki@sk.ee, CN=TEST of EE Certification Centre Root CA, O=AS Sertifitseerimiskeskus, C=EE", certificate.getIssuerDN().getName());
    }

    @Test
    public void createInvalidCertificate() {
        String invalidCertificate = ROOT_CERTIFICATE.replace("a", "b");
        assertThrows(InvalidCertificateException.class, () -> CertificateUtil.createX509Certificate(Base64.getDecoder().decode(invalidCertificate.getBytes())));
    }

    @Test
    public void certificateIsActive() {
        X509Certificate certificate = CertificateUtil.createX509Certificate(Base64.getDecoder().decode(ROOT_CERTIFICATE.getBytes()));
        assertTrue(CertificateUtil.isCertificateActive(certificate));
    }

    @Test
    public void certificateIsNotActive() {
        X509Certificate certificate = CertificateUtil.createX509Certificate(Base64.getDecoder().decode(OLD_CERTIFICATE.getBytes()));
        assertFalse(CertificateUtil.isCertificateActive(certificate));
    }

    @Test
    public void notSigningCertificate() {
        X509Certificate certificate = CertificateUtil.createX509Certificate(Base64.getDecoder().decode(OLD_CERTIFICATE.getBytes()));
        assertFalse(CertificateUtil.isSigningCertificate(certificate));
    }

    @Test
    public void isSigningCertificate() {
        X509Certificate certificate = CertificateUtil.createX509Certificate(Base64.getDecoder().decode(SIGNING_CERTIFICATE.getBytes()));
        assertTrue(CertificateUtil.isSigningCertificate(certificate));
    }

}
