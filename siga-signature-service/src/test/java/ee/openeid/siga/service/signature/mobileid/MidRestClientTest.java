package ee.openeid.siga.service.signature.mobileid;

import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.service.signature.configuration.MidRestConfigurationProperties;
import ee.sk.mid.exception.MidNotMidClientException;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.digidoc4j.DataFile;
import org.digidoc4j.DataToSign;
import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.SignatureBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.security.cert.X509Certificate;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MidRestClientTest {

    @InjectMocks
    private MidRestClient midRestClient;

    @Mock
    private MidRestConfigurationProperties configurationProperties;

    @Before
    public void setUp() {
        when(configurationProperties.getUrl()).thenReturn("https://tsp.demo.sk.ee/mid-api");
    }

    @Test(expected = MidNotMidClientException.class)
    public void invalidPhoneNo() {
        MobileIdInformation mobileIdInformation = MobileIdInformation.builder()
                .relyingPartyName("DEMO")
                .relyingPartyUUID("00000000-0000-0000-0000-000000000000")
                .phoneNo("+37292382239")
                .personIdentifier("60001019906")
                .build();

        midRestClient.getCertificate(mobileIdInformation);
    }

    @Test
    public void successfulGetCertificateRequest() {
        MobileIdInformation mobileIdInformation = createMobileIdInformation();

        X509Certificate certificate = midRestClient.getCertificate(mobileIdInformation).getCertificate();
        Assert.assertEquals("CN=TEST of ESTEID-SK 2015, OID.2.5.4.97=NTREE-10747013, O=AS Sertifitseerimiskeskus, C=EE", certificate.getIssuerDN().getName());
    }

    @Test
    public void successfulInitMobileSigning() {
        MobileIdInformation mobileIdInformation = createMobileIdInformation();
        X509Certificate signingCert = midRestClient.getCertificate(mobileIdInformation).getCertificate();
        Container container = createContainer();

        DataToSign dataToSign = createDataToSign(container, signingCert);

        InitMidSignatureResponse response = midRestClient.initMobileSigning(dataToSign, mobileIdInformation);
        Assert.assertNotNull(response.getChallengeId());
        Assert.assertNotNull(response.getSessionCode());
    }

    @Test
    public void successfulGetStatusRequest() {
        MobileIdInformation mobileIdInformation = createMobileIdInformation();
        X509Certificate signingCert = midRestClient.getCertificate(mobileIdInformation).getCertificate();
        Container container = createContainer();

        DataToSign dataToSign = createDataToSign(container, signingCert);

        InitMidSignatureResponse response = midRestClient.initMobileSigning(dataToSign, mobileIdInformation);

        GetStatusResponse statusResponse = midRestClient.getStatus(response.getSessionCode(), createMobileIdInformation());
        Assert.assertEquals("OK", statusResponse.getStatus());
        Assert.assertTrue(statusResponse.getSignature().length > 1);
    }

    private DataToSign createDataToSign(Container container, X509Certificate signingCert) {
        return SignatureBuilder
                .aSignature(container)
                .withSigningCertificate(signingCert)
                .withSignatureDigestAlgorithm(DigestAlgorithm.SHA512)
                .buildDataToSign();
    }

    private Container createContainer() {
        return ContainerBuilder.
                aContainer()
                .withDataFile(new DataFile("D0Zzjr7TcMXFLuCtlt7I9Fn7kBwspOKFIR7d+QO/FZg".getBytes(), "test1.xml", "text/plain"))
                .build();
    }

    private MobileIdInformation createMobileIdInformation() {
        return MobileIdInformation.builder()
                .relyingPartyName("DEMO")
                .relyingPartyUUID("00000000-0000-0000-0000-000000000000")
                .phoneNo("+37200000766")
                .personIdentifier("60001019906")
                .build();
    }

}
